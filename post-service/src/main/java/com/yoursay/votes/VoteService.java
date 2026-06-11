package com.yoursay.votes;

import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Public contract for the vote domain.
 */
public interface VoteService {

    Uni<List<VoteDto>> getPostVotes(Long postId);

    Uni<VoteDto> save(VoteDto vote);
}
