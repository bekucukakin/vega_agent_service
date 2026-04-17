package com.vega.agentservice.domain.dto;

import java.util.List;

/**
 * Request for conversational Q&A about a pull request.
 */
public class PrChatRequest {

    /** PR context: title, branches, description, risk info, conflict state, etc. */
    private String prContext;
    /** The user's current question (any language). */
    private String question;
    /** Previous turns for multi-turn support. */
    private List<ChatTurn> history;

    public PrChatRequest() {}

    public String getPrContext() { return prContext; }
    public void setPrContext(String prContext) { this.prContext = prContext; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public List<ChatTurn> getHistory() { return history; }
    public void setHistory(List<ChatTurn> history) { this.history = history; }

    public static class ChatTurn {
        private String role;    // "user" or "assistant"
        private String message;

        public ChatTurn() {}
        public ChatTurn(String role, String message) { this.role = role; this.message = message; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
