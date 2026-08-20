package com.yoursay.posts.dto;

import java.util.List;
import java.util.UUID;

/** Trusted provenance assembled server-side after a Pepper draft ownership check. */
public record PostCreationProvenance(UUID pepperDraftId, List<PostSourceDto> sources) {
}
