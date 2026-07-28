package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArgumentPageDto;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class UnwrappedStoryResponseAssembler {
    private UnwrappedStoryResponseAssembler() {
    }

    static List<UnwrappedArgumentPageDto> argumentPages(UnwrappedResearchDraftV1 draft) {
        Map<String, UnwrappedSourceDraftV1> sourcesById = draft.sources().stream()
                .collect(Collectors.toUnmodifiableMap(
                        UnwrappedSourceDraftV1::id,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException(
                                    "Stored Unwrapped story has duplicate source id: " + left.id());
                        }));

        return draft.pages().stream()
                .map(page -> argumentPage(page, sourcesById))
                .toList();
    }

    private static UnwrappedArgumentPageDto argumentPage(
            UnwrappedArgumentDraftV1 page,
            Map<String, UnwrappedSourceDraftV1> sourcesById
    ) {
        LinkedHashSet<String> citedIds = page.contextClaims().stream()
                .flatMap(claim -> claim.sourceIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UnwrappedSourceDraftV1> sources = citedIds.stream()
                .map(sourceId -> source(sourceId, sourcesById))
                .toList();

        return new UnwrappedArgumentPageDto(
                page.optionId(),
                page.headline(),
                page.usedCohortIds(),
                page.contextClaims(),
                page.synthesis(),
                page.caveat(),
                sources);
    }

    private static UnwrappedSourceDraftV1 source(
            String sourceId,
            Map<String, UnwrappedSourceDraftV1> sourcesById
    ) {
        UnwrappedSourceDraftV1 source = sourcesById.get(sourceId);
        if (source == null) {
            throw new IllegalStateException(
                    "Stored Unwrapped story references unknown source id: " + sourceId);
        }
        return source;
    }
}
