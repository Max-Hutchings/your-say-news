package com.yoursay.posts.dto;

import com.yoursay.posts.VotingType;
import com.yoursay.topics.dto.TopicTagDto;

import java.time.Instant;
import java.util.List;

/**
 * Public post representation for HTTP and cross-domain use. {@code media[].url}/{@code posterUrl}
 * are presigned GET URLs minted at read time (not stored).
 *
 * <p>{@code topicTags} are the post's effective topic chips, decorated in one batched query per page
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
        boolean isAiGenerated,
        Instant createdAt,
        List<PostMediaDto> media,
        List<TopicTagDto> topicTags,
        List<PostSourceDto> sources
) {
    public PostDto(Long id, Long userId, String summary, String supportQuestion, String caseFor,
                   String caseAgainst, boolean isAiGenerated, Instant createdAt, List<PostMediaDto> media) {
        this(id, userId, summary, supportQuestion, caseFor, caseAgainst, "GLOBAL", VotingType.BINARY,
                List.of(), isAiGenerated, createdAt, media, List.of(), List.of());
    }

    /** The same post with its topic chips attached — used when decorating a page of posts. */
    public PostDto withTopicTags(List<TopicTagDto> topicTags) {
        return new PostDto(id, userId, summary, supportQuestion, caseFor, caseAgainst, jurisdiction,
                votingType, voteOptions, isAiGenerated, createdAt, media, topicTags, sources);
    }
}
