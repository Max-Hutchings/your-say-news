package com.yoursay.votes.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;

/**
 * Aggregated option sentiment for a single characteristic bucket (e.g. all {@code LEFT} voters,
 * or all voters overall). <strong>Counts and percentages only — never a user id or any row that
 * could re-identify an individual.</strong> This record is the atom of the privacy contract: if a
 * field here could name a person, the contract is broken.
 *
 * @param bucket   the bucket label (an enum name like {@code "LEFT"}, or {@code "OVERALL"})
 * @param total total canonical votes in the bucket
 * @param choices stable option counts and percentages, in the post's option order
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BucketSentiment(
        String bucket,
        String label,
        IncomeRangeDisplayDto income,
        long total,
        java.util.List<ChoiceSentiment> choices
) {
    public BucketSentiment(String bucket, long total, java.util.List<ChoiceSentiment> choices) {
        this(bucket, null, null, total, choices);
    }
}
