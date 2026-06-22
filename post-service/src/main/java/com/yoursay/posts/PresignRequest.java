package com.yoursay.posts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request a presigned S3 PUT URL for a media upload. {@code contentType} must match the
 * {@code Content-Type} header the client later sends on the PUT.
 */
public record PresignRequest(
        @NotNull
        MediaType mediaType,
        @NotBlank
        @Size(max = 128)
        String contentType
) {
}
