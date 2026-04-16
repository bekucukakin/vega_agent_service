package com.vega.agentservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for conflict resolution
 * 
 * This represents a single conflict that needs to be resolved.
 * The format matches MergeConflictData.ConflictData from Vega Core.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResolutionRequest {
    
    @NotBlank(message = "filePath is required")
    @jakarta.validation.constraints.Size(max = 500, message = "filePath must not exceed 500 characters")
    private String filePath;
    
    @NotBlank(message = "language is required")
    @jakarta.validation.constraints.Pattern(
        regexp = "^(java|javascript|python|cpp|c|go|rust|typescript|csharp|kotlin|scala|clojure|haskell|ocaml|fsharp|php|swift|ruby|html|css|scss|sass|less|xml|json|yaml|bash|powershell|batch|sql|markdown|rst|toml|ini|properties|text)$",
        message = "language must be a recognized programming language or 'text'")
    private String language;
    
    @NotNull(message = "base content is required")
    @jakarta.validation.constraints.Size(max = 100000, message = "base content must not exceed 100000 characters")
    private String base;
    
    @NotNull(message = "ours content is required")
    @jakarta.validation.constraints.Size(max = 100000, message = "ours content must not exceed 100000 characters")
    private String ours;
    
    @NotNull(message = "theirs content is required")
    @jakarta.validation.constraints.Size(max = 100000, message = "theirs content must not exceed 100000 characters")
    private String theirs;
    
    @NotNull(message = "startLine is required")
    @jakarta.validation.constraints.Min(value = 1, message = "startLine must be at least 1")
    @jakarta.validation.constraints.Max(value = 1000000, message = "startLine must not exceed 1000000")
    private Integer startLine;
    
    @NotNull(message = "endLine is required")
    @jakarta.validation.constraints.Min(value = 1, message = "endLine must be at least 1")
    @jakarta.validation.constraints.Max(value = 1000000, message = "endLine must not exceed 1000000")
    private Integer endLine;
}

