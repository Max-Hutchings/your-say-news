package com.yoursay.agents.postagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agents.postagent.generator.GenerationException;
import com.yoursay.agents.postagent.generator.GenerationResult;
import com.yoursay.agents.postagent.model.AgentGenerationJob;
import com.yoursay.agents.postagent.model.AgentGenerationJobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgentJobProcessor {

    @Inject
    AgentGenerationJobRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "agent.jobs.max-attempts", defaultValue = "3")
    int maxAttempts;

    @Transactional
    public Optional<JobWork> claimNext() {
        return repository.claimable(Instant.now())
                .map(job -> {
                    job.markResearching();
                    repository.flush();
                    return new JobWork(job.getId(), job.getRequest(), job.getAttemptCount());
                });
    }

    @Transactional
    public void complete(UUID id, GenerationResult result) {
        AgentGenerationJob job = repository.findByIdOptional(id)
                .orElseThrow(() -> new IllegalStateException("Agent job disappeared: " + id));
        job.markDraftReady(objectMapper.valueToTree(result.draft()),
                result.model(), result.providerResponseId());
    }

    @Transactional
    public void fail(UUID id, int attempt, GenerationException error) {
        AgentGenerationJob job = repository.findByIdOptional(id)
                .orElseThrow(() -> new IllegalStateException("Agent job disappeared: " + id));
        if (!error.retryable() || attempt >= maxAttempts) {
            job.markFailed(error.code(), publicMessage(error));
            return;
        }
        long delayMinutes = 1L << Math.max(0, attempt - 1);
        job.markRetry(error.code(), "Generation will be retried.",
                Instant.now().plus(delayMinutes, ChronoUnit.MINUTES));
    }

    private static String publicMessage(GenerationException error) {
        return switch (error.code()) {
            case "AGENT_PROVIDER_NOT_CONFIGURED" -> "Post generation is not configured.";
            case "AGENT_INVALID_PROVIDER_OUTPUT", "AGENT_PROVIDER_RESPONSE_INVALID" ->
                    "The generated draft did not pass source validation.";
            default -> "Post generation failed. Please try again.";
        };
    }

    public record JobWork(UUID id, String request, int attempt) {
    }
}
