package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;

/**
 * Internal aggregation input: a single vote reduced to just what aggregation needs — the yes/no
 * choice and the voter's anonymised {@link CharacteristicSnapshot}. Carries no identity. Private to
 * the votes domain ({@code service} package); never crosses the domain boundary.
 */
public record VoteSnapshot(boolean voteFor, CharacteristicSnapshot snapshot) {
}
