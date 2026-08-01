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

export type UnwrappedGenerationTrigger = {
  postId: number;
  status: "RECONCILIATION_QUEUED";
};

export type UnwrappedAdminVoteOption = {
  optionId: number;
  label: string;
  ordinal: number;
  semanticKey: string;
  count: number;
  percentage: number;
};

export type UnwrappedAdminPost = {
  postId: number;
  summary: string;
  question: string;
  caseFor: string | null;
  caseAgainst: string | null;
  jurisdiction: string;
  votingType: "BINARY" | "MULTIPLE_CHOICE_SINGLE_SELECT";
  createdAt: string;
  canonicalVoteCount: number;
  overall: UnwrappedAdminVoteOption[];
};
