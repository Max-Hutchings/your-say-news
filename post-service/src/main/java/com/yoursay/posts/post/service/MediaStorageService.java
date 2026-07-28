package com.yoursay.posts.service;

import com.yoursay.posts.MediaType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Mints short-lived presigned S3 URLs so clients move bytes directly to/from S3, keeping large
 * media out of the service. Internal to the post domain.
 */
@ApplicationScoped
public class MediaStorageService {

    static final Duration UPLOAD_TTL = Duration.ofMinutes(15);
    static final Duration DOWNLOAD_TTL = Duration.ofMinutes(15);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "video/mp4", "mp4",
            "video/quicktime", "mov",
            "video/webm", "webm"
    );

    @Inject
    S3Presigner presigner;

    @ConfigProperty(name = "posts.media.bucket")
    String bucket;

    /** A presigned PUT URL plus the key the client should report back on create. */
    public record Upload(String s3Key, String uploadUrl, long expiresInSeconds) {
    }

    /**
     * Generate a unique key for the upload and a presigned PUT URL the client uploads bytes to.
     * The PUT must carry a matching {@code Content-Type} header.
     */
    public Upload presignUpload(MediaType mediaType, String contentType) {
        String key = buildKey(contentType);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_TTL)
                .putObjectRequest(objectRequest)
                .build();

        String url = presigner.presignPutObject(presignRequest).url().toString();
        return new Upload(key, url, UPLOAD_TTL.toSeconds());
    }

    /** A presigned GET URL for viewing an already-uploaded object. Null/blank keys yield null. */
    public String presignDownload(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return null;
        }

        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_TTL)
                .getObjectRequest(objectRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    private static String buildKey(String contentType) {
        String ext = EXTENSIONS.getOrDefault(contentType == null ? "" : contentType.toLowerCase(), "bin");
        return "posts/" + UUID.randomUUID() + "." + ext;
    }
}
