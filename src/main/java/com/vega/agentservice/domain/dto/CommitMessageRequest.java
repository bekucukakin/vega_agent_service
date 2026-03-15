package com.vega.agentservice.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for commit message generation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessageRequest {
    private String diff;
    private String previousMessage;
    private String feedback;
}
