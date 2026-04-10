package com.vega.agentservice.infrastructure.controller;

import com.vega.agentservice.domain.dto.CommitMessageRequest;
import com.vega.agentservice.domain.dto.CommitMessageResponse;
import com.vega.agentservice.domain.dto.ConflictResolutionRequest;
import com.vega.agentservice.domain.dto.ConflictResolutionResponse;
import com.vega.agentservice.domain.dto.PrAnalysisRequest;
import com.vega.agentservice.domain.dto.PrAnalysisResponse;
import com.vega.agentservice.domain.service.CommitMessageService;
import com.vega.agentservice.domain.service.ConflictResolutionService;
import com.vega.agentservice.infrastructure.service.GoogleAIPrAnalysisService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Vega Agent Service
 * 
 * This controller handles conflict resolution and commit message generation from Vega Core.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    
    private final ConflictResolutionService conflictResolutionService;
    private final CommitMessageService commitMessageService;
    private final GoogleAIPrAnalysisService prAnalysisService;

    @Autowired
    public AgentController(ConflictResolutionService conflictResolutionService,
                           CommitMessageService commitMessageService,
                           GoogleAIPrAnalysisService prAnalysisService) {
        this.conflictResolutionService = conflictResolutionService;
        this.commitMessageService = commitMessageService;
        this.prAnalysisService = prAnalysisService;
    }
    
    /**
     * Resolves a merge conflict using AI
     * 
     * POST /api/agent/resolve
     * 
     * Request body: ConflictResolutionRequest
     * Response: ConflictResolutionResponse
     */
    @PostMapping("/resolve")
    public ResponseEntity<ConflictResolutionResponse> resolveConflict(
            @Valid @RequestBody ConflictResolutionRequest request) {
        try {
            if (!conflictResolutionService.isAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ConflictResolutionResponse(
                        "",
                        "Conflict resolution service is not available. Please configure GOOGLE_AI_API_KEY.",
                        false
                    ));
            }
            
            ConflictResolutionResponse response = conflictResolutionService.resolveConflict(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ConflictResolutionResponse(
                    "",
                    "Failed to resolve conflict: " + e.getMessage(),
                    false
                ));
        }
    }
    
    /**
     * Generate commit message from diff.
     * POST /api/agent/commit-message
     * Request: {"diff": "...", "previousMessage": "...", "feedback": "..."}
     * Response: {"success": true, "message": "..."} or {"success": false, "error": "..."}
     */
    @PostMapping("/commit-message")
    public ResponseEntity<CommitMessageApiResponse> generateCommitMessage(
            @RequestBody(required = false) CommitMessageRequest request) {
        try {
            if (request == null || request.getDiff() == null) {
                return ResponseEntity.badRequest()
                    .body(new CommitMessageApiResponse("", false, "diff is required"));
            }
            if (!commitMessageService.isAvailable()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new CommitMessageApiResponse("", false, "Commit message service not configured. Set GOOGLE_AI_API_KEY."));
            }
            CommitMessageResponse response = commitMessageService.generateMessage(request);
            return ResponseEntity.ok(new CommitMessageApiResponse(
                response.getMessage(),
                response.isSuccess(),
                response.getError()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new CommitMessageApiResponse("", false, e.getMessage()));
        }
    }

    private static class CommitMessageApiResponse {
        private final String message;
        private final boolean success;
        private final String error;

        public CommitMessageApiResponse(String message, boolean success, String error) {
            this.message = message;
            this.success = success;
            this.error = error;
        }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }

    /**
     * AI-powered PR risk analysis.
     * POST /api/agent/pr-analysis
     * Request: PrAnalysisRequest (repositoryName, sourceBranch, targetBranch, author,
     *          filesChanged, linesAdded, linesRemoved, riskReasons, riskLevel, diffSample)
     * Response: PrAnalysisResponse (explanation, riskSummary, success, error)
     */
    @PostMapping("/pr-analysis")
    public ResponseEntity<PrAnalysisResponse> analysePr(
            @RequestBody(required = false) PrAnalysisRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(PrAnalysisResponse.failure("Request body is required"));
        }
        if (!prAnalysisService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(PrAnalysisResponse.failure("AI service not configured. Set GOOGLE_AI_API_KEY."));
        }
        PrAnalysisResponse result = prAnalysisService.analyze(request);
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    /**
     * Health check endpoint
     *
     * GET /api/agent/health
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean available = conflictResolutionService.isAvailable();
        return ResponseEntity.ok(new HealthResponse(
            "Vega Agent Service",
            available ? "READY" : "NOT_CONFIGURED",
            available
        ));
    }
    
    /**
     * Simple health response DTO
     */
    private static class HealthResponse {
        private final String service;
        private final String status;
        private final boolean available;
        
        public HealthResponse(String service, String status, boolean available) {
            this.service = service;
            this.status = status;
            this.available = available;
        }
        
        public String getService() { return service; }
        public String getStatus() { return status; }
        public boolean isAvailable() { return available; }
    }
}


