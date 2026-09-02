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
  outputInstructions: string;
  input: UnwrappedBenchmarkInput;
};

export type UnwrappedIncomeRangeDisplay = {
  bucketId: string;
  label: string;
  contextLabel: string;
  relativeLabel: string;
  marketCode: string;
  marketLabel: string;
  currencyCode: string;
  measure: "PERSONAL" | "HOUSEHOLD";
  measureLabel: string;
  lowerInclusive: number | null;
  upperExclusive: number | null;
  relativeTier: string;
  profileId: string;
  profileVersion: number;
  bandId: string;
};

export type UnwrappedBenchmarkDimension = {
  axis: string;
  bucket: string;
  label?: string;
  income?: UnwrappedIncomeRangeDisplay;
};

export type UnwrappedBenchmarkCandidate = {
  cohortId: string;
  dimensions: UnwrappedBenchmarkDimension[];
  role: "CORE_ANCHOR" | "CORE_DIFFERENTIATOR" | "TOPIC_RELEVANT" | "INTERSECTION_DISCOVERY";
  relevanceReason: string;
  sampleSize: number;
  populationSharePercentage: number;
  optionVoteCount: number;
  compositionPercentage: number;
  propensityPercentage: number;
  overIndexPercentagePoints: number;
  differenceFromRestPercentagePoints: number;
  wilson95Low: number;
  wilson95High: number;
  adjustedQValue: number;
  displayName: string;
};

export type UnwrappedBenchmarkInputOption = {
  option: { id: number; label: string; ordinal: number; semanticKey: string | null };
  overallVoteCount: number;
  overallVotePercentage: number;
  candidates: UnwrappedBenchmarkCandidate[];
  narrativeInstructions: string[];
  insufficientEvidence: string | null;
};

export type UnwrappedBenchmarkInput = {
  postId: number;
  summary: string;
  question: string;
  jurisdiction: string;
  canonicalVoteCount: number;
  aggregateVersion: string;
  options: UnwrappedBenchmarkInputOption[];
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
