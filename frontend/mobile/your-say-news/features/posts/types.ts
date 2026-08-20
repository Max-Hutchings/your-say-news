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

export interface PostSource {
  url: string;
  title: string;
  publisher: string;
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
  jurisdiction?: string;
  votingType: VotingType;
  voteOptions: VoteOption[];
  /** Derived server-side from a completed Pepper draft. */
  isAiGenerated: boolean;
  createdAt: string;
  media: PostMedia[];
  /** Effective governed topic tags, ordered by the catalogue. */
  topicTags: import("@/features/topics").TopicTag[];
  /** Selected citations shown after the article text. */
  sources: PostSource[];
}

/**
 * One page of the ranked feed (`FeedPage`). `nextCursor` is an opaque token fetching the page after
 * this one, and `null` when this is the end of the feed. Never build a cursor client-side, and never
 * treat a short page as the end — the service fills a page whenever matching posts remain.
 */
export interface FeedPage {
  posts: Post[];
  nextCursor: string | null;
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
  jurisdiction?: string;
  votingType: VotingType;
  voteOptions: { label: string }[];
  media: CreatePostMedia[];
  topicTagIds: string[];
  pepperDraftId?: string;
  citations?: PostSource[];
}

export type PepperDraftStatus = "RECEIVED" | "GENERATING" | "FINISHED" | "FAILED";

export interface PepperPostDraft {
  summary: string;
  supportQuestion: string;
  caseFor: string | null;
  caseAgainst: string | null;
  votingType: VotingType;
  voteOptions: string[];
  citations: PostSource[];
}

export interface PepperDraftRecord {
  id: string;
  prompt: string;
  replicaId: string;
  status: PepperDraftStatus;
  success: boolean | null;
  content: PepperPostDraft | null;
  errorMessage: string | null;
  publishedPostId: number | null;
  version: number;
}

export interface PepperGenerationEvent {
  status: PepperDraftStatus;
  draftId: string;
  replicaId: string;
  result?: PepperPostDraft;
  errorMessage?: string;
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
