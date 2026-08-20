package com.yoursay.posts.postagent.dto;

import java.util.List;
import java.util.UUID;

/** Server-verified provenance passed to the posts domain. */
public record AgentPublicationDto(UUID draftId, List<AgentSourceDto> sources) {
}
