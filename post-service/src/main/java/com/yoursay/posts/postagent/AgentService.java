package com.yoursay.posts.postagent;

import com.yoursay.posts.postagent.dto.AgentGenerationEventDto;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.GenerateAgentPostRequest;
import com.yoursay.posts.postagent.dto.PepperDraftDto;
import com.yoursay.posts.postagent.dto.UpdatePepperDraftRequest;
import io.smallrye.mutiny.Multi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public contract for direct, replica-local Pepper generation and persisted editable drafts. */
public interface AgentService {

    Multi<AgentGenerationEventDto> start(String authorization, GenerateAgentPostRequest request);

    Multi<AgentGenerationEventDto> events(
            UUID draftId, String requestedReplicaId, String authorization);

    Optional<PepperDraftDto> latest(String authorization);

    PepperDraftDto save(UUID draftId, UpdatePepperDraftRequest request, String authorization);

    AgentPublicationDto preparePublication(
            UUID draftId, List<AgentSourceDto> selectedSources, String authorization);

    void markPublished(UUID draftId, Long postId);
}
