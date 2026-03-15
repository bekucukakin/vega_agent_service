package com.vega.agentservice.domain.service;

import com.vega.agentservice.domain.dto.ConflictResolutionRequest;
import com.vega.agentservice.domain.dto.ConflictResolutionResponse;

/**
 * Service interface for conflict resolution
 * 
 * This service is responsible for resolving merge conflicts using AI or deterministic logic.
 */
public interface ConflictResolutionService {
    
    /**
     * Proposes a resolution for a conflict
     * 
     * @param request the conflict to resolve
     * @return proposed resolution (requires user approval in Vega Core)
     * @throws Exception if resolution fails
     */
    ConflictResolutionResponse resolveConflict(ConflictResolutionRequest request) throws Exception;
    
    /**
     * Checks if the service is available and configured
     * 
     * @return true if service is ready to use
     */
    boolean isAvailable();
}


