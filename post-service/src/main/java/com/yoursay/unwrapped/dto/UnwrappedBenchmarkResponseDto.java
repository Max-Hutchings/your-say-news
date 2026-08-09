package com.yoursay.unwrapped.dto;

import com.yoursay.posts.dto.VoteOptionDto;

import java.time.Instant;
import java.util.List;

/** Ephemeral comparison output; no returned result has entered the review lifecycle. */
public record UnwrappedBenchmarkResponseDto(
        Long postId,
        Instant generatedAt,
        List<VoteOptionDto> options,
        List<UnwrappedBenchmarkVariantDto> variants
) {
}
