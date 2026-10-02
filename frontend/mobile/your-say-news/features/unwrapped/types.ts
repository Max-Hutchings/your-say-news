import type { VoteOption } from "@/features/posts";

export type UnwrappedAvailabilityState =
  | "READY"
  | "BUILDING"
  | "REFRESHING"
  | "INSUFFICIENT_EVIDENCE"
  | "FAILED";
export type SourceClassification = "OFFICIAL" | "ACADEMIC" | "REPUTABLE_MEDIA" | "OTHER";

export interface UnwrappedSource {
  id: string;
  url: string;
  publisher: string;
  title: string;
  classification: SourceClassification;
}

export interface UnwrappedArticleParagraph {
  text: string;
  sourceIds: string[];
}

export interface UnwrappedArgumentPage {
  optionId: number;
  headline: string;
  selectedCohortIds: string[];
  paragraphs: UnwrappedArticleParagraph[];
  caveat: string;
  sources: UnwrappedSource[];
}

export interface UnwrappedStory {
  schemaVersion: "unwrapped-story-v2";
  storyId: string;
  postId: number;
  milestone: number;
  canonicalVoteCount: number;
  aggregateVersion: string;
  generatedAt: string;
  model: string;
  argumentPages: UnwrappedArgumentPage[];
  reconsiderationQuestion: string;
  reconsiderationOptions: VoteOption[];
}

export interface UnwrappedResponse {
  state: UnwrappedAvailabilityState;
  notice: string;
  originalOptionId: number;
  existingFollowUpOptionId: number | null;
  story: UnwrappedStory | null;
}

export interface FollowUpResponse {
  id: string;
  postId: number;
  storyId: string;
  originalOptionId: number;
  optionId: number;
  changed: boolean;
  createdAt: string;
}

/** Post Unwrapped feature flags served by GET /unwrapped/features (ADR-054). */
export interface UnwrappedFeatures {
  /** False hides Unwrapped completely. */
  enabled: boolean;
  /** True sends a voter to the results with an Unwrap button rather than straight into Unwrapped. */
  unwrapButton: boolean;
}

export interface UnwrapRequest {
  postId: number;
  /** False while the post is below the vote count needed to queue generation. */
  queued: boolean;
}
