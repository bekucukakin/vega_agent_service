package com.vega.agentservice.domain.dto;

/**
 * AI response to a conversational PR question.
 */
public class PrChatResponse {

    private String answer;
    private boolean success;
    private String error;

    public PrChatResponse() {}

    private PrChatResponse(String answer, boolean success, String error) {
        this.answer = answer;
        this.success = success;
        this.error = error;
    }

    public static PrChatResponse success(String answer) {
        return new PrChatResponse(answer, true, null);
    }

    public static PrChatResponse failure(String error) {
        return new PrChatResponse(null, false, error);
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
