package com.vega.agentservice.domain.dto;

import java.util.List;

/**
 * Request for conversational follow-up on a commit.
 */
public class CommitChatRequest {

    /** Short context: commit hash + message + brief diff summary (already known). */
    private String commitContext;
    /** The user's current question. */
    private String question;
    /** Previous turns: alternating user/assistant messages for multi-turn support. */
    private List<ChatTurn> history;

    public CommitChatRequest() {}

    public String getCommitContext() { return commitContext; }
    public void setCommitContext(String commitContext) { this.commitContext = commitContext; }

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
