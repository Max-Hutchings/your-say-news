package com.yoursay.posts.error;

import com.yoursay.observability.ApiException;
import com.yoursay.posts.MediaType;
import jakarta.ws.rs.core.Response;

public class PostApiException extends ApiException {

    private PostApiException(String errorCode, Response.Status status, String detailMessage) {
        super("posts", errorCode, status, detailMessage);
    }

    public static PostApiException unknownAuthor(String authorEmail) {
        return new PostApiException("POST_AUTHOR_NOT_FOUND", Response.Status.UNAUTHORIZED,
                "Cannot create post because author lookup failed: authorEmail=" + authorEmail);
    }

    public static PostApiException publishingForbidden(Long userId) {
        return new PostApiException("POST_PUBLISHING_FORBIDDEN", Response.Status.FORBIDDEN,
                "Post publishing requires an active official publisher: userId=" + userId);
    }

    public static PostApiException uploadNotOwned(String s3Key, Long userId) {
        return new PostApiException("POST_MEDIA_UPLOAD_NOT_OWNED", Response.Status.BAD_REQUEST,
                "Media upload was not created for user: s3Key=" + s3Key + ", userId=" + userId);
    }

    public static PostApiException uploadAlreadyAttached(String s3Key) {
        return new PostApiException("POST_MEDIA_UPLOAD_ALREADY_ATTACHED", Response.Status.BAD_REQUEST,
                "Media upload has already been attached: s3Key=" + s3Key);
    }

    public static PostApiException uploadExpired(String s3Key) {
        return new PostApiException("POST_MEDIA_UPLOAD_EXPIRED", Response.Status.BAD_REQUEST,
                "Media upload has expired: s3Key=" + s3Key);
    }

    public static PostApiException uploadTypeMismatch(String s3Key, MediaType expected, MediaType actual) {
        return new PostApiException("POST_MEDIA_UPLOAD_TYPE_MISMATCH", Response.Status.BAD_REQUEST,
                "Media upload type mismatch: s3Key=" + s3Key + ", expected=" + expected + ", actual=" + actual);
    }

    public static PostApiException uploadContentTypeMismatch(String s3Key, String expected, String actual) {
        return new PostApiException("POST_MEDIA_UPLOAD_CONTENT_TYPE_MISMATCH", Response.Status.BAD_REQUEST,
                "Media upload content type mismatch: s3Key=" + s3Key + ", expected=" + expected + ", actual=" + actual);
    }

    public static PostApiException mediaItemRequired() {
        return new PostApiException("POST_MEDIA_ITEM_REQUIRED", Response.Status.BAD_REQUEST,
                "Create post request contains a null media item");
    }

    public static PostApiException mediaKeysNotUnique() {
        return new PostApiException("POST_MEDIA_KEYS_NOT_UNIQUE", Response.Status.BAD_REQUEST,
                "Create post request contains duplicate media upload keys");
    }

    public static PostApiException tooManyImages(long count) {
        return new PostApiException("POST_MEDIA_TOO_MANY_IMAGES", Response.Status.BAD_REQUEST,
                "Create post request contains too many images: count=" + count + ", max=5");
    }

    public static PostApiException tooManyVideos(long count) {
        return new PostApiException("POST_MEDIA_TOO_MANY_VIDEOS", Response.Status.BAD_REQUEST,
                "Create post request contains too many videos: count=" + count + ", max=1");
    }

    public static PostApiException mediaTypeRequired(MediaType mediaType, String contentType) {
        return new PostApiException("POST_MEDIA_TYPE_REQUIRED", Response.Status.BAD_REQUEST,
                "Media type and content type are required: mediaType=" + mediaType + ", contentType=" + contentType);
    }

    public static PostApiException invalidImageContentType(String contentType) {
        return new PostApiException("POST_MEDIA_INVALID_IMAGE_CONTENT_TYPE", Response.Status.BAD_REQUEST,
                "Image upload received non-image content type: contentType=" + contentType);
    }

    public static PostApiException invalidVideoContentType(String contentType) {
        return new PostApiException("POST_MEDIA_INVALID_VIDEO_CONTENT_TYPE", Response.Status.BAD_REQUEST,
                "Video upload received non-video content type: contentType=" + contentType);
    }
}
