package com.vega.agentservice.domain.dto;

import java.util.List;

/**
 * Request for AI-powered PR risk analysis and contextual explanation.
 */
public class PrAnalysisRequest {

    private String repositoryName;
    private String sourceBranch;
    private String targetBranch;
    private String author;
    private List<String> filesChanged;
    private int linesAdded;
    private int linesRemoved;
    private List<String> riskReasons;     // rule-based reasons already computed
    private String riskLevel;             // LOW / MEDIUM / HIGH
    private String diffSample;            // optional: first ~3KB of unified diff

    public PrAnalysisRequest() {}

    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public String getSourceBranch() { return sourceBranch; }
    public void setSourceBranch(String sourceBranch) { this.sourceBranch = sourceBranch; }

    public String getTargetBranch() { return targetBranch; }
    public void setTargetBranch(String targetBranch) { this.targetBranch = targetBranch; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public List<String> getFilesChanged() { return filesChanged; }
    public void setFilesChanged(List<String> filesChanged) { this.filesChanged = filesChanged; }

    public int getLinesAdded() { return linesAdded; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }

    public int getLinesRemoved() { return linesRemoved; }
    public void setLinesRemoved(int linesRemoved) { this.linesRemoved = linesRemoved; }

    public List<String> getRiskReasons() { return riskReasons; }
    public void setRiskReasons(List<String> riskReasons) { this.riskReasons = riskReasons; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getDiffSample() { return diffSample; }
    public void setDiffSample(String diffSample) { this.diffSample = diffSample; }
}
