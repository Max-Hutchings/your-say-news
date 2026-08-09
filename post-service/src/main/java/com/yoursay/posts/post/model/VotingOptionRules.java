package com.yoursay.posts.model;

import com.yoursay.posts.VotingType;
import com.yoursay.posts.error.PostApiException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/** Pure validation and normalisation for a post's immutable option definitions. */
public final class VotingOptionRules {
    public static final int MIN_MULTIPLE_CHOICE_OPTIONS = 2;
    public static final int MAX_MULTIPLE_CHOICE_OPTIONS = 5;
    public static final int MAX_LABEL_LENGTH = 120;

    private VotingOptionRules() {
    }

    public record Definition(String label, int ordinal, String semanticKey) {
    }

    public static List<Definition> normalize(VotingType requestedType, List<String> requestedLabels) {
        VotingType type = requestedType == null ? VotingType.BINARY : requestedType;
        List<String> labels = requestedLabels == null ? List.of() : requestedLabels;
        if (type == VotingType.BINARY) {
            if (!labels.isEmpty()) {
                throw PostApiException.invalidVoteOptions("binary posts use server-owned options");
            }
            return List.of(
                    new Definition("Agree", 0, "AGREE"),
                    new Definition("Disagree", 1, "DISAGREE"));
        }

        if (labels.size() < MIN_MULTIPLE_CHOICE_OPTIONS || labels.size() > MAX_MULTIPLE_CHOICE_OPTIONS) {
            throw PostApiException.invalidVoteOptions("multiple-choice posts require 2 to 5 options");
        }
        Set<String> seen = new HashSet<>();
        return IntStream.range(0, labels.size()).mapToObj(ordinal -> {
            String raw = labels.get(ordinal);
            String label = raw == null ? "" : raw.trim();
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
                throw PostApiException.invalidVoteOptions("option labels must contain 1 to 120 characters");
            }
            if (!seen.add(label.toLowerCase(Locale.ROOT))) {
                throw PostApiException.invalidVoteOptions("option labels must be case-insensitively distinct");
            }
            return new Definition(label, ordinal, null);
        }).toList();
    }
}
