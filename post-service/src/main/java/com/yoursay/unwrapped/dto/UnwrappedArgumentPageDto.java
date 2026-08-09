package com.yoursay.unwrapped.dto;

import java.util.List;

public record UnwrappedArgumentPageDto(
        Long optionId,
        String headline,
        List<String> selectedCohortIds,
        List<UnwrappedArticleParagraphDraftV2> paragraphs,
        String caveat,
        List<UnwrappedSourceDraftV1> sources
) {
}
