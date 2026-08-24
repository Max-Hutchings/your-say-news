package com.yoursay.posts.postagent;

import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.AutoPostAgentDraftDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Trusted server-side contract used by auto-post for the fixed official publisher. */
public interface AutoPostAgentService {

    UUID startForPublisher(long publisherUserId, String prompt);

    Optional<AutoPostAgentDraftDto> getForPublisher(UUID draftId, long publisherUserId);

    AgentPublicationDto preparePublicationForPublisher(
            UUID draftId, long publisherUserId, List<AgentSourceDto> selectedSources);

    void markPublished(UUID draftId, Long postId);
}
