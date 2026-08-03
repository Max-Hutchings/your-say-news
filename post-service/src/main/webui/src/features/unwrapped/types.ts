export type UnwrappedReviewStatus = "DRAFT" | "APPROVED" | "REJECTED";

export type UnwrappedArticleParagraph = {
  text: string;
  sourceIds: string[];
};

export type UnwrappedArgument = {
  optionId: number;
  headline: string;
  selectedCohortIds: string[];
  paragraphs: UnwrappedArticleParagraph[];
  caveat: string;
  sources: UnwrappedSource[];
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
  notice: string;
  options: Array<{ id: number; label: string; ordinal: number; semanticKey: string | null }>;
  argumentPages: UnwrappedArgument[];
};

export type UnwrappedReviewError = {
  status: number | null;
  message: string;
};

export type UnwrappedGenerationTrigger = {
  postId: number;
  status: "RECONCILIATION_QUEUED";
};

export type UnwrappedGenerationState =
  | "NOT_STARTED"
  | "QUEUED"
  | "GENERATING"
  | "READY_FOR_REVIEW"
  | "FAILED";

export type UnwrappedGenerationStatus = {
  postId: number;
  state: UnwrappedGenerationState;
  queuedJobs: number;
  generatingJobs: number;
  readyJobs: number;
  failedJobs: number;
  updatedAt: string | null;
  errorMessage: string | null;
};

export type UnwrappedGenerationMonitor = {
  workerAvailable: boolean;
  refreshedAt: string;
  statuses: UnwrappedGenerationStatus[];
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

export type UnwrappedBenchmarkPrompt = {
  systemPrompt: string;
};

export type UnwrappedBenchmarkVariant = {
  position: number;
  systemPrompt: string;
  effectiveSystemPrompt: string;
  attemptCount: number;
  status: "SUCCEEDED" | "FAILED";
  model: string | null;
  providerResponseId: string | null;
  argumentPages: UnwrappedArgument[];
  errorCode: string | null;
  errorMessage: string | null;
};

export type UnwrappedBenchmarkResponse = {
  postId: number;
  generatedAt: string;
  options: Array<{ id: number; label: string; ordinal: number; semanticKey: string | null }>;
  variants: UnwrappedBenchmarkVariant[];
};
