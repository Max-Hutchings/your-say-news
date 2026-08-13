package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArgumentPageDto;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;

import java.util.LinkedHashMap;
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

    /**
     * Adapts an unchecked benchmark draft for display without applying publication rules.
     * Missing collections and unresolved source references remain visible as empty presentation
     * sections instead of turning the whole model response into a failed lane.
     */
    static List<UnwrappedArgumentPageDto> benchmarkArgumentPages(UnwrappedResearchDraftV1 draft) {
        if (draft == null || draft.pages() == null) return List.of();

        Map<String, UnwrappedSourceDraftV1> sourcesById = new LinkedHashMap<>();
        safeList(draft.sources()).stream()
                .filter(source -> source != null && source.id() != null)
                .forEach(source -> sourcesById.putIfAbsent(source.id(), source));

        return draft.pages().stream()
                .filter(page -> page != null)
                .map(page -> benchmarkArgumentPage(page, sourcesById))
                .toList();
    }

    private static UnwrappedArgumentPageDto benchmarkArgumentPage(
            UnwrappedArgumentDraftV1 page,
            Map<String, UnwrappedSourceDraftV1> sourcesById
    ) {
        List<UnwrappedArticleParagraphDraftV2> paragraphs = safeList(page.paragraphs()).stream()
                .filter(paragraph -> paragraph != null)
                .map(paragraph -> new UnwrappedArticleParagraphDraftV2(
                        paragraph.text(), safeList(paragraph.sourceIds())))
                .toList();
        LinkedHashSet<String> citedIds = paragraphs.stream()
                .flatMap(paragraph -> paragraph.sourceIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UnwrappedSourceDraftV1> sources = citedIds.stream()
                .map(sourcesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new UnwrappedArgumentPageDto(
                page.optionId(), page.headline(), safeList(page.selectedCohortIds()),
                paragraphs, page.caveat(), sources);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static UnwrappedArgumentPageDto argumentPage(
            UnwrappedArgumentDraftV1 page,
            Map<String, UnwrappedSourceDraftV1> sourcesById
    ) {
        LinkedHashSet<String> citedIds = page.paragraphs().stream()
                .flatMap(paragraph -> paragraph.sourceIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UnwrappedSourceDraftV1> sources = citedIds.stream()
                .map(sourceId -> source(sourceId, sourcesById))
                .toList();

        return new UnwrappedArgumentPageDto(
                page.optionId(),
                page.headline(),
                page.selectedCohortIds(),
                page.paragraphs(),
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
