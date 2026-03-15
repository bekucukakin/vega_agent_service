package com.vega.agentservice.infrastructure.service;

import com.vega.agentservice.domain.dto.ConflictResolutionRequest;
import com.vega.agentservice.domain.dto.ConflictResolutionResponse;
import com.vega.agentservice.domain.service.ConflictResolutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google AI Studio (Gemini) Conflict Resolution Service
 * 
 * This service uses Google AI Studio API to resolve merge conflicts.
 * API key must be configured via GOOGLE_AI_API_KEY environment variable.
 */
@Service
public class GoogleAIConflictResolutionService implements ConflictResolutionService {
    
    // Öncelik: yüksek kotası olan modeller önce. 429/kota hatasında sıradakine geç
    private static final String API_ENDPOINT_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/%s:generateContent";
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
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    
    private final String apiKey;
    private final HttpClient httpClient;
    private final boolean enabled;
    
    public GoogleAIConflictResolutionService(@Value("${google.ai.api-key:}") String apiKey) {
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
    public ConflictResolutionResponse resolveConflict(ConflictResolutionRequest request) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("Google AI API key not configured. Set GOOGLE_AI_API_KEY environment variable.");
        }
        
        String prompt = buildPrompt(request);
        String response = callGoogleAI(prompt);
        return parseResponse(response, request);
    }
    
    /**
     * Builds the prompt for Google AI
     */
    private String buildPrompt(ConflictResolutionRequest conflict) {
        return String.format("""
            You are resolving a merge conflict.
            
            File: %s
            Language: %s
            
            BASE:
            %s
            
            OURS:
            %s
            
            THEIRS:
            %s
            
            Rules:
            - Modify ONLY conflicted lines
            - Preserve formatting
            - Prefer minimal changes
            - If unsure, choose the safer option
            
            Return ONLY:
            
            RESOLVED_CODE:
            <code>
            
            EXPLANATION:
            <short explanation>
            """,
            conflict.getFilePath(),
            conflict.getLanguage(),
            conflict.getBase(),
            conflict.getOurs(),
            conflict.getTheirs()
        );
    }
    
    /**
     * Calls Google AI Studio API
     * Tries different model names until one works
     */
    private String callGoogleAI(String prompt) throws Exception {
        // Google AI Studio API format
        String requestBody = String.format("""
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "%s"
                    }
                  ]
                }
              ],
              "generationConfig": {
                "temperature": 0.3,
                "topK": 40,
                "topP": 0.95,
                "maxOutputTokens": 2000
              }
            }
            """,
            escapeJson(prompt)
        );
        
        // Try different model names
        Exception lastException = null;
        for (String modelName : MODEL_NAMES) {
            String url = String.format(API_ENDPOINT_TEMPLATE, modelName) + "?key=" + apiKey;
            
            try {
                return callGoogleAIWithModel(url, requestBody);
            } catch (Exception e) {
                lastException = e;
                if (shouldTryNextModel(e)) {
                    continue;
                }
                throw e;
            }
        }
        
        throw new Exception("All model names failed. Last error: " + 
            (lastException != null ? lastException.getMessage() : "Unknown"));
    }

    /** 404, 429, kota, rate limit hatalarında sonraki model denenir */
    private static boolean shouldTryNextModel(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("404") || msg.contains("not_found")
            || msg.contains("429") || msg.contains("resource_exhausted")
            || msg.contains("quota") || msg.contains("rate limit")
            || msg.contains("503");
    }
    
    /**
     * Calls Google AI API with specific model URL
     */
    private String callGoogleAIWithModel(String url, String requestBody) throws Exception {
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(TIMEOUT)
            .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new Exception("Google AI API error: " + response.statusCode() + " - " + response.body());
            }
            
            return extractContentFromResponse(response.body());
        } catch (IOException | InterruptedException e) {
            throw new Exception("Failed to call Google AI API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts content from Google AI JSON response
     */
    private String extractContentFromResponse(String jsonResponse) {
        try {
            // Try to parse JSON properly - extract text from candidates[0].content.parts[0].text
            Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                String text = matcher.group(1);
                // Unescape JSON strings properly
                text = text.replace("\\n", "\n")
                          .replace("\\r", "\r")
                          .replace("\\t", "\t")
                          .replace("\\\"", "\"")
                          .replace("\\\\", "\\")
                          .replace("\\u003c", "<")
                          .replace("\\u003e", ">");
                return text;
            }
            
            // Fallback: try simpler pattern
            pattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*(?:\\\\.[^\"]*)*)\"", Pattern.DOTALL);
            matcher = pattern.matcher(jsonResponse);
            if (matcher.find()) {
                String text = matcher.group(1);
                return unescapeJsonString(text);
            }
            
            // Last resort: return as-is (might be already unescaped)
            return jsonResponse;
        } catch (Exception e) {
            return jsonResponse;
        }
    }
    
    private String unescapeJsonString(String str) {
        return str.replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\u003c", "<")
                  .replace("\\u003e", ">")
                  .replace("\\u0026", "&");
    }
    
    /**
     * Parses AI response into ConflictResolutionResponse
     */
    private ConflictResolutionResponse parseResponse(String response, ConflictResolutionRequest conflict) {
        String resolvedCode = "";
        String explanation = "";
        List<String> warnings = new ArrayList<>();
        boolean safeToApply = true;
        
        // Parse RESOLVED_CODE section
        Pattern codePattern = Pattern.compile("RESOLVED_CODE:\\s*([\\s\\S]*?)(?=EXPLANATION:|$)", Pattern.CASE_INSENSITIVE);
        Matcher codeMatcher = codePattern.matcher(response);
        if (codeMatcher.find()) {
            resolvedCode = codeMatcher.group(1).trim();
            // Remove markdown code blocks if present
            resolvedCode = resolvedCode.replaceAll("^```[\\w]*\\n", "").replaceAll("\\n```$", "");
            resolvedCode = resolvedCode.trim();
        } else {
            // Fallback: try to extract code between markers
            String[] parts = response.split("EXPLANATION:");
            if (parts.length > 0) {
                resolvedCode = parts[0].trim();
                resolvedCode = resolvedCode.replaceAll("^RESOLVED_CODE:\\s*", "").trim();
            }
        }
        
        // Parse EXPLANATION section
        Pattern explanationPattern = Pattern.compile("EXPLANATION:\\s*([\\s\\S]*)", Pattern.CASE_INSENSITIVE);
        Matcher explanationMatcher = explanationPattern.matcher(response);
        if (explanationMatcher.find()) {
            explanation = explanationMatcher.group(1).trim();
        }
        
        // Safety checks
        if (resolvedCode.isEmpty()) {
            warnings.add("AI did not provide resolved code");
            safeToApply = false;
        }
        
        if (resolvedCode.contains("<<<<<<<") || resolvedCode.contains("=======") || resolvedCode.contains(">>>>>>>")) {
            warnings.add("Resolved code still contains conflict markers");
            safeToApply = false;
        }
        
        // Check if resolved code is too different (might indicate error)
        int baseLength = conflict.getBase().length();
        int oursLength = conflict.getOurs().length();
        int theirsLength = conflict.getTheirs().length();
        int resolvedLength = resolvedCode.length();
        
        int avgLength = (baseLength + oursLength + theirsLength) / 3;
        if (avgLength > 0 && Math.abs(resolvedLength - avgLength) > avgLength * 2) {
            warnings.add("Resolved code length differs significantly from input");
            safeToApply = false;
        }
        
        return new ConflictResolutionResponse(resolvedCode, explanation, warnings, safeToApply);
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

