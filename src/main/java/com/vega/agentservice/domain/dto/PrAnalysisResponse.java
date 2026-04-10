package com.vega.agentservice.domain.dto;

/**
 * AI-generated contextual explanation of PR risk.
 */
public class PrAnalysisResponse {

    private String explanation;   // narrative description of what the changes do
    private String riskSummary;   // one-sentence AI risk assessment
    private boolean success;
    private String error;

    public PrAnalysisResponse() {}

    public PrAnalysisResponse(String explanation, String riskSummary, boolean success, String error) {
        this.explanation = explanation;
        this.riskSummary = riskSummary;
        this.success = success;
        this.error = error;
    }

    public static PrAnalysisResponse success(String explanation, String riskSummary) {
        return new PrAnalysisResponse(explanation, riskSummary, true, null);
    }

    public static PrAnalysisResponse failure(String error) {
        return new PrAnalysisResponse(null, null, false, error);
    }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
