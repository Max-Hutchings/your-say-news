package com.yoursay.posts.dto;

/**
 * Response to a presign request: the S3 key the client should send back in the create-post body,
 * the presigned PUT URL to upload bytes to, and how long that URL is valid.
 */
public record PresignResponse(
        String s3Key,
        String uploadUrl,
        long expiresInSeconds
) {
}
