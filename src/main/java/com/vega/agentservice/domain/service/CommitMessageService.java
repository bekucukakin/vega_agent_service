package com.vega.agentservice.domain.service;

import com.vega.agentservice.domain.dto.CommitMessageRequest;
import com.vega.agentservice.domain.dto.CommitMessageResponse;

/**
 * Service interface for AI-generated commit messages.
 */
public interface CommitMessageService {
    boolean isAvailable();
    CommitMessageResponse generateMessage(CommitMessageRequest request) throws Exception;
}
