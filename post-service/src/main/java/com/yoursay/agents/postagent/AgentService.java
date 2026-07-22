package com.yoursay.agents.postagent;

import java.util.Optional;
import java.util.UUID;

/**
 * Public contract for asynchronous unbiased-post generation.
 */
public interface AgentService {

    AgentJobDto start(String callerEmail, String authorization, GenerateAgentPostRequest request);

    Optional<AgentJobDto> get(UUID jobId, String callerEmail, String authorization);
}
