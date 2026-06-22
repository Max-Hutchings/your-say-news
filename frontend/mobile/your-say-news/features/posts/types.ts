/**
 * Posts domain types — mirror the shared Stage 2 API contract (see
 * docs/plans/stage2-posts.md). These cross the post-service boundary, so the
 * shapes here must match the backend DTOs exactly.
 */

/** The kinds of media a post can carry. */
export type MediaType = "IMAGE" | "VIDEO";

/**
 * A media item on a post as returned by the service. `url` (and `posterUrl`
 * for video) are short-lived presigned GET URLs minted at read time — render
 * from those, never from the raw `s3Key`.
 */
export interface PostMedia {
  mediaType: MediaType;
  s3Key: string;
  contentType: string;
  posterS3Key: string | null;
  url: string;
  posterUrl: string | null;
}

/** A post as returned by the service (`PostDto`). */
export interface Post {
  id: number;
  userId: number;
  title: string;
  summary: string;
  supportQuestion: string;
  /** Only the Stage 6 agent sets this true; it drives the unbiased badge. */
  isUnbiased: boolean;
  createdAt: string;
  media: PostMedia[];
}

/**
 * A media reference sent up when creating a post — the bytes are already in S3
 * (via presign + PUT), so we send only the key and its descriptors.
 */
export interface CreatePostMedia {
  mediaType: MediaType;
  s3Key: string;
  contentType: string;
  posterS3Key: string | null;
}

/** The body for `POST /posts`. The author is taken from the token, never the body. */
export interface CreatePostInput {
  title: string;
  summary: string;
  supportQuestion: string;
  media: CreatePostMedia[];
}

/** `POST /posts/media/presign` request. */
export interface PresignRequest {
  mediaType: MediaType;
  contentType: string;
}

/** `POST /posts/media/presign` response. */
export interface PresignResponse {
  s3Key: string;
  uploadUrl: string;
  expiresInSeconds: number;
}
