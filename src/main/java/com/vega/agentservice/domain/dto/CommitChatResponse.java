package com.vega.agentservice.domain.dto;

/**
 * AI response to a conversational commit question.
 */
public class CommitChatResponse {

    private String answer;
    private boolean success;
    private String error;

    public CommitChatResponse() {}

    private CommitChatResponse(String answer, boolean success, String error) {
        this.answer = answer;
        this.success = success;
        this.error = error;
    }

    public static CommitChatResponse success(String answer) {
        return new CommitChatResponse(answer, true, null);
    }

    public static CommitChatResponse failure(String error) {
        return new CommitChatResponse(null, false, error);
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
