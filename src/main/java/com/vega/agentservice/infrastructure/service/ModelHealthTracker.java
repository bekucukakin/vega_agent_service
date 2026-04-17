package com.vega.agentservice.infrastructure.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks runtime model health and temporary cooldown windows.
 * This is an adaptive fallback layer when provider quota/rate-limit is hit.
 */
@Component
public class ModelHealthTracker {

    private static final Duration DEFAULT_RATE_LIMIT_COOLDOWN = Duration.ofMinutes(2);
    private static final Duration DEFAULT_TRANSIENT_COOLDOWN = Duration.ofSeconds(30);

    private static class ModelState {
        private final AtomicInteger failures = new AtomicInteger(0);
        private volatile Instant cooldownUntil = Instant.EPOCH;
        private volatile String lastError = "";
        private volatile Instant lastUpdated = Instant.EPOCH;
    }

    private final Map<String, ModelState> stateByModel = new ConcurrentHashMap<>();

    public List<String> orderCandidates(String[] preferredModels) {
        List<String> candidates = new ArrayList<>();
        Instant now = Instant.now();
        for (String model : preferredModels) {
            ModelState st = stateByModel.get(model);
            if (st == null || !st.cooldownUntil.isAfter(now)) {
                candidates.add(model);
            }
        }
        if (!candidates.isEmpty()) return candidates;

        // If every model is on cooldown, still try the soonest-to-recover one first.
        List<String> all = new ArrayList<>(List.of(preferredModels));
        all.sort(Comparator.comparing(m -> stateByModel.getOrDefault(m, new ModelState()).cooldownUntil));
        return all;
    }

    public void recordSuccess(String model) {
        ModelState st = stateByModel.computeIfAbsent(model, k -> new ModelState());
        st.failures.set(0);
        st.cooldownUntil = Instant.EPOCH;
        st.lastError = "";
        st.lastUpdated = Instant.now();
    }

    public void recordFailure(String model, int statusCode, String errorBody) {
        ModelState st = stateByModel.computeIfAbsent(model, k -> new ModelState());
        st.failures.incrementAndGet();
        st.lastError = trim(errorBody, 300);
        st.lastUpdated = Instant.now();

        if (statusCode == 429 || containsRateLimit(errorBody)) {
            st.cooldownUntil = Instant.now().plus(DEFAULT_RATE_LIMIT_COOLDOWN);
        } else if (statusCode == 503 || statusCode == 500 || statusCode == 504) {
            st.cooldownUntil = Instant.now().plus(DEFAULT_TRANSIENT_COOLDOWN);
        }
    }

    public void recordFailure(String model, String message) {
        ModelState st = stateByModel.computeIfAbsent(model, k -> new ModelState());
        st.failures.incrementAndGet();
        st.lastError = trim(message, 300);
        st.lastUpdated = Instant.now();
        if (containsRateLimit(message)) {
            st.cooldownUntil = Instant.now().plus(DEFAULT_RATE_LIMIT_COOLDOWN);
        }
    }

    public List<Map<String, Object>> snapshot(String[] preferredModels) {
        Instant now = Instant.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String model : preferredModels) {
            ModelState st = stateByModel.getOrDefault(model, new ModelState());
            long cooldownSeconds = Math.max(0, Duration.between(now, st.cooldownUntil).getSeconds());
            Map<String, Object> row = new HashMap<>();
            row.put("model", model);
            row.put("available", cooldownSeconds == 0);
            row.put("cooldownSeconds", cooldownSeconds);
            row.put("failures", st.failures.get());
            row.put("lastError", st.lastError);
            row.put("remainingQuota", "unknown_via_runtime_api");
            out.add(row);
        }
        return out;
    }

    private static boolean containsRateLimit(String s) {
        if (s == null) return false;
        String m = s.toLowerCase();
        return m.contains("429")
                || m.contains("resource_exhausted")
                || m.contains("rate limit")
                || m.contains("quota");
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
