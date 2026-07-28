export type UnwrappedReviewStatus = "DRAFT" | "APPROVED" | "REJECTED";

export type UnwrappedClaim = {
  id: string;
  statement: string;
  sourceIds: string[];
  interpretation: boolean;
};

export type UnwrappedArgument = {
  optionId: number;
  headline: string;
  usedCohortIds: string[];
  contextClaims: UnwrappedClaim[];
  synthesis: string;
  caveat: string;
};

export type UnwrappedSource = {
  id: string;
  url: string;
  publisher: string;
  title: string;
  classification: "OFFICIAL" | "ACADEMIC" | "REPUTABLE_MEDIA" | "OTHER";
};

export type UnwrappedReviewStory = {
  storyId: string;
  postId: number;
  milestone: number;
  canonicalVoteCount: number;
  status: UnwrappedReviewStatus;
  generatedAt: string;
  draft: {
    pages: UnwrappedArgument[];
    sources: UnwrappedSource[];
  };
};

export type UnwrappedReviewError = {
  status: number | null;
  message: string;
};

export type ForcedUnwrappedJob = {
  jobId: string;
  postId: number;
  milestone: number;
  status: "PENDING" | "GENERATING" | "DRAFT_READY" | "FAILED";
  created: boolean;
};
