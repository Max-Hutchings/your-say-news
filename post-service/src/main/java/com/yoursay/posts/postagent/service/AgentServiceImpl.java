package com.yoursay.posts.postagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentJobDto;
import com.yoursay.posts.postagent.AgentService;
import com.yoursay.posts.postagent.dto.GenerateAgentPostRequest;
import com.yoursay.posts.postagent.client.AgentUserClient;
import com.yoursay.posts.postagent.error.AgentApiException;
import com.yoursay.posts.postagent.model.AgentGenerationJob;
import com.yoursay.posts.postagent.model.AgentGenerationJobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgentServiceImpl implements AgentService {

    @Inject
    AgentGenerationJobRepository repository;

    @Inject
    AgentUserClient userClient;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Transactional
    public AgentJobDto start(String callerEmail, String authorization, GenerateAgentPostRequest request) {
        Long userId = resolveUser(callerEmail, authorization, true).userId();
        AgentGenerationJob job = new AgentGenerationJob(userId, request.request().trim());
        repository.persist(job);
        repository.flush();
        return toDto(job);
    }

    @Override
    public Optional<AgentJobDto> get(UUID jobId, String callerEmail, String authorization) {
        Long userId = resolveUser(callerEmail, authorization, false).userId();
        return repository.findOwned(jobId, userId).map(this::toDto);
    }

    private AgentUserClient.UserAccess resolveUser(String callerEmail, String authorization,
                                                   boolean publishingRequired) {
        try {
            AgentUserClient.UserAccess user = userClient.getCurrentUserAccess(authorization);
            if (user == null || user.userId() == null) {
                throw AgentApiException.userMissing(callerEmail);
            }
            if (publishingRequired && !user.isActiveOfficialPublisher()) {
                throw AgentApiException.publishingForbidden(user.userId());
            }
            return user;
        } catch (AgentApiException e) {
            throw e;
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 404) {
                throw AgentApiException.userMissing(callerEmail);
            }
            throw AgentApiException.userLookupFailed(callerEmail, status);
        }
    }

    private AgentJobDto toDto(AgentGenerationJob job) {
        return new AgentJobDto(
                job.getId(),
                job.getStatus(),
                job.getAttemptCount(),
                job.getModel(),
                toDraft(job),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getPublishedPostId(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }

    private AgentDraftDto toDraft(AgentGenerationJob job) {
        if (job.getDraft() == null) {
            return null;
        }
        try {
            return objectMapper.treeToValue(job.getDraft(), AgentDraftDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored agent draft is invalid: jobId=" + job.getId(), e);
        }
    }
}
