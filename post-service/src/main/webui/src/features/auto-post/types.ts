export type AutoPostRegion = "UK" | "US" | "GLOBAL";

export type AutoPostRunStatus =
  | "QUEUED"
  | "DISCOVERING"
  | "CANDIDATES_READY"
  | "DRAFTING"
  | "DRAFT_READY"
  | "PUBLISHING"
  | "FAILED"
  | "PUBLISHED";

export interface AutoPostSource {
  url: string;
  title: string;
  publisher: string;
}

export interface AutoPostCandidate {
  id: string;
  rank: number;
  region: AutoPostRegion;
  headline: string;
  summary: string;
  publishedAt: string;
  sources: AutoPostSource[];
}

export interface AutoPostDraft {
  id: string;
  summary: string;
  supportQuestion: string;
  caseFor: string | null;
  caseAgainst: string | null;
  votingType: "BINARY" | "MULTIPLE_CHOICE";
  voteOptions: string[];
  citations: AutoPostSource[];
  version: number;
}

export interface AutoPostRun {
  id: string;
  status: AutoPostRunStatus;
  windowStart: string;
  windowEnd: string;
  candidates: AutoPostCandidate[];
  selectedCandidateId: string | null;
  pepperDraftId: string | null;
  draft: AutoPostDraft | null;
  publishedPostId: number | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AutoPostEvent {
  run: AutoPostRun;
}

export interface AutoPostError {
  status: number | null;
  message: string;
}
