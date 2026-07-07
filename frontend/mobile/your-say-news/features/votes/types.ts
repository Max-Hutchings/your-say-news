/**
 * Votes domain types — mirror the post-service votes API contract. These shapes cross the
 * post-service boundary, so they must match the backend DTOs exactly.
 *
 * PII boundary: the vote a caller gets back says only that a vote exists with this stance —
 * never who cast it. Identity lives in the bearer token, never in these shapes.
 */

/**
 * A vote as returned by post-service (`VoteResponseDto`). `voteFor` is the stance on the
 * support question: `true` = agree, `false` = disagree.
 */
export interface Vote {
  id: number;
  postId: number;
  voteFor: boolean;
}

/**
 * Why a vote attempt failed, so the feed can message each case precisely:
 * - `duplicate` — the user has already voted (backend 409); the UI locks rather than errors.
 * - `auth` — the session is rejected (401/403); the user must sign in again.
 * - `network` — the request never reached the server.
 * - `unknown` — any other failure.
 */
export type VoteErrorKind = "duplicate" | "auth" | "network" | "unknown";

/**
 * One characteristic bucket's vote split, as returned by post-service
 * (`BucketSentimentDto`). `bucket` is a raw enum name (e.g. `LEFT`, `AGE_25_34`) — prettify for
 * display. Counts and percentages ONLY: a bucket never names or reveals an individual voter, and
 * the shape carries no identity. `yesPct`/`noPct` are 0–100 with one decimal.
 */
export interface BucketSentiment {
  bucket: string;
  yesCount: number;
  noCount: number;
  total: number;
  yesPct: number;
  noPct: number;
}

/**
 * The aggregated sentiment for a post — overall, or sliced by one characteristic axis
 * (`SentimentBreakdownDto`). `characteristic` is the backend axis field name (e.g.
 * `politicalPersuasion`) or the literal `"OVERALL"`. `buckets` arrive largest-first (backend
 * sorts). `suppressedBuckets` counts buckets withheld by the privacy `k`-threshold (0 for MVP1).
 */
export interface SentimentBreakdown {
  postId: number;
  characteristic: string;
  buckets: BucketSentiment[];
  suppressedBuckets: number;
}

/**
 * Why a sentiment fetch failed:
 * - `notVoted` — the caller hasn't voted yet (backend 403); results are gated behind voting.
 * - `auth` — the session is rejected (401); sign in again.
 * - `network` — the request never reached the server.
 * - `unknown` — any other failure.
 */
export type SentimentErrorKind = "notVoted" | "auth" | "network" | "unknown";
