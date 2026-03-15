package com.vega.agentservice.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for commit message generation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessageResponse {
    private String message;
    private boolean success;
    private String error;
}
