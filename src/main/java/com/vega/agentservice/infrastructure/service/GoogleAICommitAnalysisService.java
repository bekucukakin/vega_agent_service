package com.vega.agentservice.infrastructure.service;

import com.vega.agentservice.domain.dto.CommitAnalysisRequest;
import com.vega.agentservice.domain.dto.CommitAnalysisResponse;
import com.vega.agentservice.domain.dto.CommitChatRequest;
import com.vega.agentservice.domain.dto.CommitChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Google AI (Gemini) service for commit analysis and conversational follow-up.
 *
 * Two capabilities:
 *  1. analyze()  — structured one-shot analysis of a commit (summary / changes / risks)
 *  2. chat()     — multi-turn conversational Q&A about a commit
 */
@Service
public class GoogleAICommitAnalysisService {

    private static final String API_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";

    private static final String[] MODEL_NAMES = {
        "models/gemini-3.1-flash-lite",
        "models/gemini-2.5-flash-lite",
        "models/gemini-3-flash-preview",
        "models/gemma-3-27b-it",
        "models/gemini-2.5-flash",
        "models/gemini-2.5-pro",
        "models/gemini-2.0-flash",
        "models/gemini-1.5-pro",
        "models/gemini-1.5-flash",
        "models/gemini-pro"
    };

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final HttpClient httpClient;
    private final boolean enabled;

    public GoogleAICommitAnalysisService(@Value("${google.ai.api-key:}") String apiKey) {
        String key = (apiKey != null && !apiKey.isEmpty()) ? apiKey : System.getenv("GOOGLE_AI_API_KEY");
        this.apiKey = key;
        this.enabled = (key != null && !key.isEmpty());
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    public boolean isAvailable() {
        return enabled;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1. Structured commit analysis
    // ──────────────────────────────────────────────────────────────────────

    public CommitAnalysisResponse analyze(CommitAnalysisRequest req) {
        if (!enabled) {
            return CommitAnalysisResponse.failure("Google AI API key not configured. Set GOOGLE_AI_API_KEY.");
        }
        try {
            String prompt = buildAnalysisPrompt(req);
            String raw = callGoogleAI(prompt);
            return parseAnalysisResponse(raw);
        } catch (Exception e) {
            return CommitAnalysisResponse.failure("Commit analysis failed: " + e.getMessage());
        }
    }

    private String buildAnalysisPrompt(CommitAnalysisRequest req) {
        String diff = req.getDiff() != null && !req.getDiff().isBlank()
                ? req.getDiff() : "(diff not provided)";
        // Truncate very large diffs to stay within token budget
        if (diff.length() > 6000) diff = diff.substring(0, 6000) + "\n... (truncated)";

        return """
You are a senior software engineer reviewing a git commit. Analyse the commit carefully.

Respond ONLY in this exact format — no extra text:

SUMMARY:
<2-4 sentences describing what this commit does and why, in plain language>

CHANGES:
<bullet list, each line starts with "- "; list the key code changes, files affected, and logic modifications>

RISKS:
<bullet list, each line starts with "- "; list security concerns, missing tests, potential bugs, or edge cases. If none, write "- No significant risks detected.">

RISK_LEVEL: <LOW|MEDIUM|HIGH>

---
Commit: %s
Author: %s
Message: %s

Diff:
%s
""".formatted(
                nvl(req.getCommitHash()),
                nvl(req.getAuthor()),
                nvl(req.getCommitMessage()),
                diff
        );
    }

    private CommitAnalysisResponse parseAnalysisResponse(String raw) {
        String summary = extractSection(raw, "SUMMARY", "CHANGES");
        String changes = extractSection(raw, "CHANGES", "RISKS");
        String risks = extractSection(raw, "RISKS", "RISK_LEVEL");
        String riskLevel = "LOW";

        Matcher rm = Pattern.compile("RISK_LEVEL:\\s*(LOW|MEDIUM|HIGH)", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (rm.find()) riskLevel = rm.group(1).toUpperCase();

        if (summary.isEmpty() && changes.isEmpty()) {
            summary = raw.trim();
        }

        return CommitAnalysisResponse.success(summary, changes, risks, riskLevel);
    }

    private String extractSection(String text, String from, String to) {
        Pattern p = Pattern.compile(
                from + ":\\s*([\\s\\S]*?)(?=" + to + ":|RISK_LEVEL:|$)",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    // ──────────────────────────────────────────────────────────────────────
    // 2. Conversational Q&A about a commit
    // ──────────────────────────────────────────────────────────────────────

    public CommitChatResponse chat(CommitChatRequest req) {
        if (!enabled) {
            return CommitChatResponse.failure("Google AI API key not configured. Set GOOGLE_AI_API_KEY.");
        }
        try {
            String prompt = buildChatPrompt(req);
            String raw = callGoogleAI(prompt);
            return CommitChatResponse.success(raw.trim());
        } catch (Exception e) {
            return CommitChatResponse.failure("Chat failed: " + e.getMessage());
        }
    }

    private String buildChatPrompt(CommitChatRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
You are a helpful code assistant. A developer is asking questions about a specific git commit.
Answer concisely and accurately. If unsure, say so.

Commit context:
""");
        sb.append(nvl(req.getCommitContext())).append("\n\n");

        List<CommitChatRequest.ChatTurn> history = req.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("Previous conversation:\n");
            for (CommitChatRequest.ChatTurn turn : history) {
                sb.append(turn.getRole().equals("user") ? "Developer: " : "Assistant: ");
                sb.append(turn.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Developer: ").append(nvl(req.getQuestion())).append("\nAssistant:");
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────
    // HTTP + model fallback
    // ──────────────────────────────────────────────────────────────────────

    private String callGoogleAI(String prompt) throws Exception {
        String body = """
            {
              "contents": [{"parts": [{"text": "%s"}]}],
              "generationConfig": {
                "temperature": 0.3,
                "topK": 40,
                "topP": 0.95,
                "maxOutputTokens": 1024
              }
            }
            """.formatted(escapeJson(prompt));

        Exception last = null;
        for (String model : MODEL_NAMES) {
            String url = String.format(API_ENDPOINT_TEMPLATE, model) + "?key=" + apiKey;
            try {
                return callWithModel(url, body);
            } catch (Exception e) {
                last = e;
                if (shouldTryNext(e)) continue;
                throw e;
            }
        }
        throw new Exception("All Gemini models failed. Last: " + (last != null ? last.getMessage() : "?"));
    }

    private String callWithModel(String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(TIMEOUT)
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new Exception("Gemini API error " + resp.statusCode() + ": " + resp.body());
            }
            return extractText(resp.body());
        } catch (IOException | InterruptedException e) {
            throw new Exception("HTTP error: " + e.getMessage(), e);
        }
    }

    private static boolean shouldTryNext(Exception e) {
        String m = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return m.contains("404") || m.contains("not_found")
            || m.contains("429") || m.contains("resource_exhausted")
            || m.contains("quota") || m.contains("rate limit")
            || m.contains("503");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private String extractText(String json) {
        Pattern p = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*+)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) return unescape(m.group(1));
        return json;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\u003c", "<").replace("\\u003e", ">").replace("\\u0026", "&");
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
