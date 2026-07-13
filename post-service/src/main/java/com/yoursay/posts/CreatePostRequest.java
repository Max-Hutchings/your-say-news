package com.yoursay.posts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for creating a post. The author is derived from the authenticated token, never this body,
 * and {@code isUnbiased} is always forced false on create (only the Stage 7 agent sets it).
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
        @Size(max = 8)
        List<@NotNull @Valid Media> media
) {
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
