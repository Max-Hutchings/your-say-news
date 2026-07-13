package com.yoursay.posts;

import java.time.Instant;
import java.util.List;

/**
 * Public post representation for HTTP and cross-domain use. {@code media[].url}/{@code posterUrl}
 * are presigned GET URLs minted at read time (not stored).
 */
public record PostDto(
        Long id,
        Long userId,
        String summary,
        String supportQuestion,
        String caseFor,
        String caseAgainst,
        boolean isUnbiased,
        Instant createdAt,
        List<PostMediaDto> media
) {
}
