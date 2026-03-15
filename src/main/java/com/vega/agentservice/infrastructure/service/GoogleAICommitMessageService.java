package com.vega.agentservice.infrastructure.service;

import com.vega.agentservice.domain.dto.CommitMessageRequest;
import com.vega.agentservice.domain.dto.CommitMessageResponse;
import com.vega.agentservice.domain.service.CommitMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google AI (Gemini) Commit Message Generation Service
 */
@Service
public class GoogleAICommitMessageService implements CommitMessageService {

    private static final String API_ENDPOINT_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";
    /** Öncelik: yüksek kotası olan modeller önce. 429/kota hatasında sıradakine geç */
    private static final String[] MODEL_NAMES = {
        "models/gemini-3.1-flash-lite",  // 1. 500 RPD – en yüksek kota
        "models/gemini-2.5-flash-lite",  // 2. 20 RPD – ayrı kota
        "models/gemini-3-flash-preview", // 3. 20 RPD
        "models/gemma-3-27b-it",         // 4. 14.4K RPD
        "models/gemini-2.5-flash",       // 5. 20 RPD
        "models/gemini-2.5-pro",         // 6.
        "models/gemini-2.0-flash",       // 7.
        "models/gemini-1.5-pro",         // 8.
        "models/gemini-1.5-flash",       // 9.
        "models/gemini-pro"              // 10. son çare
    };
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final String apiKey;
    private final HttpClient httpClient;
    private final boolean enabled;

    public GoogleAICommitMessageService(@Value("${google.ai.api-key:}") String apiKey) {
        this.apiKey = apiKey != null && !apiKey.isEmpty() ? apiKey : System.getenv("GOOGLE_AI_API_KEY");
        this.enabled = this.apiKey != null && !this.apiKey.isEmpty();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public CommitMessageResponse generateMessage(CommitMessageRequest request) throws Exception {
        if (!enabled) {
            return new CommitMessageResponse("", false, "Google AI API key not configured");
        }

        try {
            String prompt = buildPrompt(request);
            String response = callGoogleAI(prompt);
            String message = extractMessage(response);
            return new CommitMessageResponse(message, true, null);
        } catch (Exception e) {
            return new CommitMessageResponse("", false, e.getMessage());
        }
    }

    private String buildPrompt(CommitMessageRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a conventional commit message for the following staged diff.\n\n");
        sb.append("FORMAT (strict):\n");
        sb.append("type(scope): short description (max 72 chars)\n");
        sb.append("\n");
        sb.append("* Bullet point 1\n");
        sb.append("* Bullet point 2\n\n");
        sb.append("TYPES: feat, fix, refactor, test, docs, chore, style, perf\n");
        sb.append("RULES: Use imperative mood (\"add\" not \"added\"). Be specific. Analyze files changed, lines +/- , modules.\n\n");

        if (req.getPreviousMessage() != null && !req.getPreviousMessage().isEmpty()
                && req.getFeedback() != null && !req.getFeedback().isEmpty()) {
            sb.append("Previous message (rejected): ").append(req.getPreviousMessage()).append("\n");
            sb.append("User feedback: ").append(req.getFeedback()).append("\n");
            sb.append("Generate a NEW message incorporating the feedback.\n\n");
        }

        sb.append("DIFF:\n");
        sb.append(req.getDiff());
        sb.append("\n\nReturn ONLY the commit message (title + optional bullet list), no quotes or extra text.");
        return sb.toString();
    }

    private String callGoogleAI(String prompt) throws Exception {
        String requestBody = String.format("""
            {
              "contents": [{"parts": [{"text": "%s"}]}],
              "generationConfig": {"temperature": 0.3, "maxOutputTokens": 200}
            }
            """, escapeJson(prompt));

        Exception lastEx = null;
        for (String modelName : MODEL_NAMES) {
            String url = String.format(API_ENDPOINT_TEMPLATE, modelName) + "?key=" + apiKey;
            try {
                return callWithModel(url, requestBody);
            } catch (Exception e) {
                lastEx = e;
                if (shouldTryNextModel(e)) {
                    continue;
                }
                throw e;
            }
        }
        throw lastEx != null ? lastEx : new Exception("All models failed");
    }

    /** 404, 429, kota, rate limit hatalarında sonraki model denenir */
    private static boolean shouldTryNextModel(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("404") || msg.contains("not_found")
            || msg.contains("429") || msg.contains("resource_exhausted")
            || msg.contains("quota") || msg.contains("rate limit")
            || msg.contains("503");
    }

    private String callWithModel(String url, String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(TIMEOUT)
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("API error: " + response.statusCode() + " - " + response.body());
        }
        return extractContentFromResponse(response.body());
    }

    private String extractContentFromResponse(String json) {
        Pattern p = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        }
        return json;
    }

    private String extractMessage(String response) {
        String s = response.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"");
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
