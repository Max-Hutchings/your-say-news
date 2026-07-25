import type { VoteOption } from "@/features/posts";

export type UnwrappedAvailabilityState =
  | "READY"
  | "BUILDING"
  | "REFRESHING"
  | "INSUFFICIENT_EVIDENCE"
  | "FAILED";
export type UnwrappedMode = "PREDICTION" | "OBSERVED";
export type SourceClassification = "OFFICIAL" | "ACADEMIC" | "REPUTABLE_MEDIA" | "OTHER";

export interface UnwrappedSource {
  id: string;
  url: string;
  publisher: string;
  title: string;
  classification: SourceClassification;
}

export interface UnwrappedClaim {
  id: string;
  statement: string;
  sourceIds: string[];
  interpretation: boolean;
}

export interface UnwrappedArgumentPage {
  optionId: number;
  headline: string;
  usedCohortIds: string[];
  predictedCohorts: string[];
  contextClaims: UnwrappedClaim[];
  synthesis: string;
  caveat: string;
}

export interface UnwrappedStory {
  schemaVersion: "unwrapped-story-v1";
  storyId: string;
  postId: number;
  mode: UnwrappedMode;
  milestone: number | null;
  canonicalVoteCount: number;
  aggregateVersion: string | null;
  generatedAt: string;
  model: string;
  argumentPages: UnwrappedArgumentPage[];
  sources: UnwrappedSource[];
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
