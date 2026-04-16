package com.vega.agentservice.domain.dto;

/**
 * Request payload for AI-powered commit analysis.
 */
public class CommitAnalysisRequest {

    private String commitHash;
    private String commitMessage;
    private String author;
    private String diff;          // unified diff of the commit

    public CommitAnalysisRequest() {}

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }
}
