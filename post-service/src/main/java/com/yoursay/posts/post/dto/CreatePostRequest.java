package com.yoursay.posts.dto;

import com.yoursay.posts.MediaType;
import com.yoursay.posts.Orientation;
import com.yoursay.posts.VotingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body for creating a post. The author is derived from the authenticated token, never this body,
 * AI provenance is derived from a verified Pepper draft ID, never a client boolean.
 */
public record CreatePostRequest(
        @NotBlank
        @Size(max = 4000)
        String summary,
        @NotBlank
        @Size(max = 512)
        String supportQuestion,
        // Optional one-line arguments shown as the "case for" / "case against" cards.
        @Size(max = 512)
        String caseFor,
        @Size(max = 512)
        String caseAgainst,
        /** ISO 3166 country code, governed region code, or GLOBAL. Defaults to GLOBAL. */
        @Size(max = 32)
        String jurisdiction,
        VotingType votingType,
        @Size(max = 5)
        List<@NotNull @Valid VoteOption> voteOptions,
        @Size(max = 8)
        List<@NotNull @Valid Media> media,
        /**
         * Optional governed topic tag IDs, at most three. IDs only: there is no
         * arbitrary-label path. An unknown or retired id fails the request rather than being
         * dropped, so an author never publishes believing a topic was applied.
         */
        @Size(max = 3)
        List<@NotBlank @Size(max = 64) String> topicTagIds,
        UUID pepperDraftId,
        @Size(max = 20) List<@NotNull @Valid Citation> citations
) {
    public CreatePostRequest(String summary, String supportQuestion, String caseFor,
                             String caseAgainst, String jurisdiction, VotingType votingType,
                             List<VoteOption> voteOptions, List<Media> media,
                             List<String> topicTagIds) {
        this(summary, supportQuestion, caseFor, caseAgainst, jurisdiction, votingType,
                voteOptions, media, topicTagIds, null, List.of());
    }

    public record VoteOption(@NotBlank @Size(max = 120) String label) {
    }

    public record Citation(
            @NotBlank @Size(max = 2048) String url,
            @NotBlank @Size(max = 512) String title,
            @NotBlank @Size(max = 256) String publisher
    ) {
    }

    /**
     * A media item the client has already uploaded to S3 (via a presigned PUT) and is now
     * attaching by its key.
     */
    public record Media(
            @NotNull
            MediaType mediaType,
            // Optional; defaults to LANDSCAPE on the server when the client doesn't classify the asset.
            Orientation orientation,
            @NotBlank
            @Size(max = 1024)
            String s3Key,
            @NotBlank
            @Size(max = 128)
            String contentType,
            @Size(max = 1024)
            String posterS3Key
    ) {
    }
}
