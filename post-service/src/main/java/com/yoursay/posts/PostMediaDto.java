package com.yoursay.posts;

/**
 * A media item on a post. {@code url} and {@code posterUrl} are short-lived presigned GET URLs
 * minted at read time; the persisted state is the {@code s3Key}/{@code posterS3Key} references.
 */
public record PostMediaDto(
        MediaType mediaType,
        String s3Key,
        String contentType,
        String posterS3Key,
        String url,
        String posterUrl
) {
}
