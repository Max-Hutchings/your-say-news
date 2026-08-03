package com.yoursay.unwrapped.selection;

import com.yoursay.posts.dto.VoteOptionDto;
import java.util.List;

/**
 * Bounded evidence and research guidance for generating one voting option's argument.
 *
 * @param option immutable option identity and label the argument must represent
 * @param overallVoteCount number of post voters who selected the option
 * @param overallVotePercentage option's share of all votes on the post
 * @param candidates statistically safe cohorts the model may use as observed context
 * @param prohibitedInferences claims the model must avoid to preserve statistical integrity
 * @param insufficientEvidence explanation supplied when no cohort passes the narration rules
 */
public record OptionBriefV1(
        VoteOptionDto option,
        long overallVoteCount,
        double overallVotePercentage,
        List<SelectedCohortV1> candidates,
        List<String> prohibitedInferences,
        String insufficientEvidence
) {
}
