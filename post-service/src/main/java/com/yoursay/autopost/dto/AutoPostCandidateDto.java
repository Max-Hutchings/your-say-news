package com.yoursay.autopost.dto;

import com.yoursay.autopost.AutoPostRegion;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutoPostCandidateDto(
        UUID id,
        int rank,
        AutoPostRegion region,
        String headline,
        String summary,
        Instant publishedAt,
        List<AutoPostSourceDto> sources
) {
}
