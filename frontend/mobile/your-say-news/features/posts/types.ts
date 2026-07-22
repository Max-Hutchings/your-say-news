/**
 * Posts domain types — mirror the shared Stage 2 API contract (see
 * docs/plans/stage2-posts.md). These cross the post-service boundary, so the
 * shapes here must match the backend DTOs exactly.
 */

/** The kinds of media a post can carry. */
export type MediaType = "IMAGE" | "VIDEO";
export type FeedPostType = "VIDEO" | "ARTICLE";
export type VotingType = "BINARY" | "MULTIPLE_CHOICE";

export interface VoteOption {
  id: number;
  label: string;
  ordinal: number;
  semanticKey: "AGREE" | "DISAGREE" | null;
}

/**
 * How a media item is shaped, so the feed sizes it deterministically: LANDSCAPE renders in a fixed
 * 16:9 box; PORTRAIT in a tall centred box (which collapses the summary to a "see more" line).
 */
export type MediaOrientation = "LANDSCAPE" | "PORTRAIT";

/**
 * A media item on a post as returned by the service. `url` (and `posterUrl`
 * for video) are short-lived presigned GET URLs minted at read time — render
 * from those, never from the raw `s3Key`.
 */
export interface PostMedia {
  mediaType: MediaType;
  orientation: MediaOrientation;
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
  summary: string;
  supportQuestion: string;
  /** Optional one-line arguments shown as the "case for" / "case against" cards. */
  caseFor: string | null;
  caseAgainst: string | null;
  votingType: VotingType;
  voteOptions: VoteOption[];
  /** Only the Stage 7 agent sets this true; it drives the unbiased badge. */
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
  orientation: MediaOrientation;
  s3Key: string;
  contentType: string;
  posterS3Key: string | null;
}

/** The body for `POST /posts`. The author is taken from the token, never the body. */
export interface CreatePostInput {
  summary: string;
  supportQuestion: string;
  caseFor: string | null;
  caseAgainst: string | null;
  votingType: VotingType;
  voteOptions: { label: string }[];
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
