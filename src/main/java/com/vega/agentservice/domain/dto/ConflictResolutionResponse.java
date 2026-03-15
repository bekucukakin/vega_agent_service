package com.vega.agentservice.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for conflict resolution
 * 
 * This represents the AI's proposed resolution for a conflict.
 * The format matches AIResolution from Vega Core.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResolutionResponse {
    
    /**
     * The resolved code content (conflict markers removed)
     * Max length: 100000 characters
     */
    @jakarta.validation.constraints.Size(max = 100000, message = "resolvedContent must not exceed 100000 characters")
    private String resolvedContent;
    
    /**
     * Brief explanation of the resolution decision
     * Max length: 1000 characters
     */
    @jakarta.validation.constraints.Size(max = 1000, message = "explanation must not exceed 1000 characters")
    private String explanation;
    
    /**
     * List of warnings about the resolution (if any)
     * Max items: 10, each warning max 500 characters
     */
    @jakarta.validation.constraints.Size(max = 10, message = "warnings must not exceed 10 items")
    private List<@jakarta.validation.constraints.Size(max = 500) String> warnings;
    
    /**
     * Whether it's safe to apply this resolution automatically
     * false = requires manual review
     */
    @jakarta.validation.constraints.NotNull(message = "safeToApply is required")
    private Boolean safeToApply;
    
    public ConflictResolutionResponse(String resolvedContent, String explanation, Boolean safeToApply) {
        this.resolvedContent = resolvedContent;
        this.explanation = explanation;
        this.warnings = new ArrayList<>();
        this.safeToApply = safeToApply;
    }
}

