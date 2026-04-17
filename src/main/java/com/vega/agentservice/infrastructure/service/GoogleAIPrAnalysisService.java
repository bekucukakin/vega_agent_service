package com.vega.agentservice.infrastructure.service;

import com.vega.agentservice.domain.dto.PrAnalysisRequest;
import com.vega.agentservice.domain.dto.PrAnalysisResponse;
import com.vega.agentservice.domain.dto.PrChatRequest;
import com.vega.agentservice.domain.dto.PrChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google AI (Gemini) powered PR Risk Analysis Service.
 *
 * Sends PR context (changed files, risk reasons, diff sample) to Gemini and
 * returns a contextual, human-readable explanation + one-sentence risk summary.
 */
@Service
public class GoogleAIPrAnalysisService {

    private static final String API_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";

    private static final String[] MODEL_NAMES = {
        "models/gemini-2.5-flash-lite",
        "models/gemini-2.5-flash",
        "models/gemini-2.0-flash"
    };

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(25);

    private final String apiKey;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final ModelHealthTracker modelHealthTracker;

    public GoogleAIPrAnalysisService(@Value("${google.ai.api-key:}") String apiKey,
                                     ModelHealthTracker modelHealthTracker) {
        String key = (apiKey != null && !apiKey.isEmpty()) ? apiKey : System.getenv("GOOGLE_AI_API_KEY");
        this.apiKey = key;
        this.enabled = key != null && !key.isEmpty();
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.modelHealthTracker = modelHealthTracker;
    }

    public boolean isAvailable() {
        return enabled;
    }

    public List<Map<String, Object>> getModelHealthSnapshot() {
        return modelHealthTracker.snapshot(MODEL_NAMES);
    }

    /**
     * Analyses a PR and returns contextual AI explanation + risk summary + structured findings.
     */
    public PrAnalysisResponse analyze(PrAnalysisRequest req) {
        if (!enabled) {
            return PrAnalysisResponse.failure("Google AI API key not configured. Set GOOGLE_AI_API_KEY.");
        }
        try {
            String prompt = buildPrompt(req);
            String raw = callGoogleAI(prompt);
            return parseResponse(raw);
        } catch (Exception e) {
            return PrAnalysisResponse.failure("AI analysis failed: " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Prompt
    // ──────────────────────────────────────────────────────────────────────

    private String buildPrompt(PrAnalysisRequest req) {
        String files = req.getFilesChanged() == null ? "(none)" :
                req.getFilesChanged().stream().collect(Collectors.joining(", "));

        String reasons = req.getRiskReasons() == null || req.getRiskReasons().isEmpty() ? "(none)" :
                req.getRiskReasons().stream()
                   .map(r -> "- " + r)
                   .collect(Collectors.joining("\n"));

        String diff = (req.getDiffSample() != null && !req.getDiffSample().isBlank())
                ? req.getDiffSample()
                : "(diff not provided)";

        String prTypeLabel = req.getPrType() != null ? req.getPrType().replace('_', ' ') : "UNSPECIFIED";

        return """
You are a senior software engineer performing a code review. Analyse the following Pull Request carefully.

Respond ONLY in this exact format (do not add extra text outside the sections):

EXPLANATION:
<3-6 sentences describing what the changes do, what functionality is affected, and architectural impact>

RISK_SUMMARY:
<single sentence, max 20 words, capturing the most important risk>

FINDINGS:
<List each concrete finding on its own line in this exact format:>
FINDING: SEVERITY=<HIGH|MEDIUM|LOW> CATEGORY=<SECURITY|LOGIC|PERFORMANCE|TESTING|CODE_QUALITY|DEPENDENCY|ARCHITECTURE> SCORE=<integer 1-20> TEXT=<one sentence finding description>
<Add 2-5 FINDING lines. If no issues found, write: FINDING: SEVERITY=LOW CATEGORY=CODE_QUALITY SCORE=0 TEXT=No significant issues detected.>

---
Pull Request Information:
Repository : %s
Author     : %s
Branch     : %s → %s
PR Type    : %s
Risk Level : %s (from rule-based analysis)

Changed Files (%d):
%s

Rule-Based Risk Reasons (already computed):
%s

Diff Sample:
%s
""".formatted(
                nvl(req.getRepositoryName()),
                nvl(req.getAuthor()),
                nvl(req.getSourceBranch()), nvl(req.getTargetBranch()),
                prTypeLabel,
                nvl(req.getRiskLevel()),
                req.getFilesChanged() == null ? 0 : req.getFilesChanged().size(),
                files,
                reasons,
                diff
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    // HTTP
    // ──────────────────────────────────────────────────────────────────────

    private String callGoogleAI(String prompt) throws Exception {
        String requestBody = """
            {
              "contents": [{"parts": [{"text": "%s"}]}],
              "generationConfig": {
                "temperature": 0.4,
                "topK": 40,
                "topP": 0.95,
                "maxOutputTokens": 1024
              }
            }
            """.formatted(escapeJson(prompt));

        Exception last = null;
        Instant deadline = Instant.now().plus(TOTAL_TIMEOUT);
        for (String model : modelHealthTracker.orderCandidates(MODEL_NAMES)) {
            if (Instant.now().isAfter(deadline)) {
                throw new Exception("AI request timed out before model fallback completed.");
            }
            String url = String.format(API_ENDPOINT_TEMPLATE, model) + "?key=" + apiKey;
            try {
                String answer = callWithModel(url, requestBody);
                modelHealthTracker.recordSuccess(model);
                return answer;
            } catch (Exception e) {
                last = e;
                modelHealthTracker.recordFailure(model, e.getMessage());
                if (shouldTryNext(e)) continue;
                throw e;
            }
        }
        throw new Exception("All Gemini models failed quickly. Last: " + (last != null ? last.getMessage() : "?"));
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
    // Response parsing
    // ──────────────────────────────────────────────────────────────────────

    private String extractText(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*+)\"", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return unescape(m.group(1));
        }
        return json;
    }

    private PrAnalysisResponse parseResponse(String raw) {
        String explanation = "";
        String riskSummary = "";
        List<String> findings = new ArrayList<>();

        java.util.regex.Matcher em = java.util.regex.Pattern
                .compile("EXPLANATION:\\s*([\\s\\S]*?)(?=RISK_SUMMARY:|FINDINGS:|$)",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (em.find()) explanation = em.group(1).trim();

        java.util.regex.Matcher rm = java.util.regex.Pattern
                .compile("RISK_SUMMARY:\\s*([\\s\\S]*?)(?=FINDINGS:|$)",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        if (rm.find()) riskSummary = rm.group(1).trim();

        // Parse structured findings
        java.util.regex.Matcher fm = java.util.regex.Pattern
                .compile("FINDING:\\s*SEVERITY=(\\w+)\\s+CATEGORY=(\\w+)\\s+SCORE=(\\d+)\\s+TEXT=(.+)",
                         java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(raw);
        while (fm.find()) {
            String severity = fm.group(1).toUpperCase();
            String category = fm.group(2).toUpperCase();
            String score = fm.group(3);
            String text = fm.group(4).trim();
            // format: "SEVERITY:::CATEGORY:::description:::scoreDelta"
            findings.add(severity + ":::" + category + ":::" + text + ":::" + score);
        }

        if (explanation.isEmpty() && riskSummary.isEmpty()) {
            explanation = raw.trim();
            riskSummary = "See explanation for details.";
        }

        return PrAnalysisResponse.success(explanation, riskSummary, findings);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────────────
    // PR Conversational Chat
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Multi-turn conversational Q&A about a pull request.
     * Detects the user's language from the question and replies in the same language.
     */
    public PrChatResponse prChat(PrChatRequest req) {
        if (!enabled) {
            return PrChatResponse.failure("AI service not configured. Set GOOGLE_AI_API_KEY.");
        }
        try {
            String prompt = buildPrChatPrompt(req);
            String raw = callGoogleAI(prompt);
            return PrChatResponse.success(raw.trim());
        } catch (Exception e) {
            return PrChatResponse.failure("PR chat failed: " + e.getMessage());
        }
    }

    private String buildPrChatPrompt(PrChatRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
You are a helpful code review assistant for the VEGA version control system.
A developer is asking questions about a specific pull request.

IMPORTANT: Detect the language of the developer's question and respond in EXACTLY that language.
If the question is in Turkish, respond in Turkish. If in English, respond in English. Match the developer's language precisely.

The latest developer question is the highest-priority instruction.
Always use full PR context and conversation history below.
When useful, cite concrete files, diffs, risk signals, and metrics from the context.
Do not ignore the user's question in favor of generic summaries.
If context is missing, say exactly what is missing.
Provide clear, detailed, and helpful answers. Be thorough but concise.

Pull Request context:
""");
        sb.append(nvl(req.getPrContext())).append("\n\n");

        List<PrChatRequest.ChatTurn> history = req.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("Previous conversation:\n");
            for (PrChatRequest.ChatTurn turn : history) {
                sb.append(turn.getRole().equals("user") ? "Developer: " : "Assistant: ");
                sb.append(turn.getMessage()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Latest developer question (MOST IMPORTANT): ")
          .append(nvl(req.getQuestion()))
          .append("\nAssistant:");
        return sb.toString();
    }
}
