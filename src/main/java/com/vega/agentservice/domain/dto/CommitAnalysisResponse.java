package com.vega.agentservice.domain.dto;

/**
 * Structured AI analysis of a single commit.
 */
public class CommitAnalysisResponse {

    /** One-paragraph plain-language description of what the commit does. */
    private String summary;
    /** Short bullet list of what changed (files, logic, etc.). */
    private String changes;
    /** Risk or gap assessment: security issues, missing tests, potential bugs. */
    private String risks;
    /** One-line overall verdict: LOW / MEDIUM / HIGH risk. */
    private String riskLevel;

    private boolean success;
    private String error;

    public CommitAnalysisResponse() {}

    private CommitAnalysisResponse(String summary, String changes, String risks,
                                   String riskLevel, boolean success, String error) {
        this.summary = summary;
        this.changes = changes;
        this.risks = risks;
        this.riskLevel = riskLevel;
        this.success = success;
        this.error = error;
    }

    public static CommitAnalysisResponse success(String summary, String changes,
                                                  String risks, String riskLevel) {
        return new CommitAnalysisResponse(summary, changes, risks, riskLevel, true, null);
    }

    public static CommitAnalysisResponse failure(String error) {
        return new CommitAnalysisResponse(null, null, null, null, false, error);
    }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getChanges() { return changes; }
    public void setChanges(String changes) { this.changes = changes; }

    public String getRisks() { return risks; }
    public void setRisks(String risks) { this.risks = risks; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
