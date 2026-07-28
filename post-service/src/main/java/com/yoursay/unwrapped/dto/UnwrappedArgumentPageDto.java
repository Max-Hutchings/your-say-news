package com.yoursay.unwrapped.dto;

import java.util.List;

public record UnwrappedArgumentPageDto(
        Long optionId,
        String headline,
        List<String> usedCohortIds,
        List<UnwrappedClaimDraftV1> contextClaims,
        String synthesis,
        String caveat,
        List<UnwrappedSourceDraftV1> sources
) {
}
