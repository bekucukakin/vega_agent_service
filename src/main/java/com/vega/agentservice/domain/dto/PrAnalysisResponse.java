package com.vega.agentservice.domain.dto;

import java.util.List;

/**
 * AI-generated contextual explanation of PR risk.
 * findings format: "SEVERITY:::CATEGORY:::description:::scoreDelta"
 * e.g. "HIGH:::SECURITY:::Potential SQL injection in auth.py:::15"
 */
public class PrAnalysisResponse {

    private String explanation;   // narrative description of what the changes do
    private String riskSummary;   // one-sentence AI risk assessment
    private List<String> findings; // structured AI findings (severity:::category:::description:::delta)
    private boolean success;
    private String error;

    public PrAnalysisResponse() {}

    public PrAnalysisResponse(String explanation, String riskSummary, List<String> findings,
                               boolean success, String error) {
        this.explanation = explanation;
        this.riskSummary = riskSummary;
        this.findings = findings;
        this.success = success;
        this.error = error;
    }

    public static PrAnalysisResponse success(String explanation, String riskSummary, List<String> findings) {
        return new PrAnalysisResponse(explanation, riskSummary, findings, true, null);
    }

    public static PrAnalysisResponse failure(String error) {
        return new PrAnalysisResponse(null, null, null, false, error);
    }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getRiskSummary() { return riskSummary; }
    public void setRiskSummary(String riskSummary) { this.riskSummary = riskSummary; }

    public List<String> getFindings() { return findings; }
    public void setFindings(List<String> findings) { this.findings = findings; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
