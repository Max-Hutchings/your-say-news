package com.yoursay.posts.dto;

import com.yoursay.posts.VotingType;
import com.yoursay.topics.dto.TopicDto;

import java.time.Instant;
import java.util.List;

/**
 * Public post representation for HTTP and cross-domain use. {@code media[].url}/{@code posterUrl}
 * are presigned GET URLs minted at read time (not stored).
 *
 * <p>{@code topics} are the post's effective topic chips, decorated in one batched query per page
 * (ADR-043). Empty when the post carries none — a post without topics is valid, it is simply not
 * reachable through category discovery.
 */
public record PostDto(
        Long id,
        Long userId,
        String summary,
        String supportQuestion,
        String caseFor,
        String caseAgainst,
        String jurisdiction,
        VotingType votingType,
        List<VoteOptionDto> voteOptions,
        boolean isUnbiased,
        Instant createdAt,
        List<PostMediaDto> media,
        List<TopicDto> topics
) {
    public PostDto(Long id, Long userId, String summary, String supportQuestion, String caseFor,
                   String caseAgainst, boolean isUnbiased, Instant createdAt, List<PostMediaDto> media) {
        this(id, userId, summary, supportQuestion, caseFor, caseAgainst, "GLOBAL", VotingType.BINARY,
                List.of(), isUnbiased, createdAt, media, List.of());
    }

    /** The same post with its topic chips attached — used when decorating a page of posts. */
    public PostDto withTopics(List<TopicDto> topics) {
        return new PostDto(id, userId, summary, supportQuestion, caseFor, caseAgainst, jurisdiction,
                votingType, voteOptions, isUnbiased, createdAt, media, topics);
    }
}
