/**
 * Votes feature — public face.
 *
 * Routes and other features import votes ONLY from here, never from the internal services/,
 * hooks/ or non-public components.
 */

export { VoteControls } from "./components/VoteControls";
export { SentimentResults } from "./components/SentimentResults";
export { SentimentResultsSheet } from "./components/SentimentResultsSheet";
export { useVote } from "./hooks/use-vote";
export { useSentiment } from "./hooks/use-sentiment";
export { castVote, getMyVote } from "./services/VoteService";
export { getOverallSentiment, getAxisSentiment } from "./services/SentimentService";

export type {
  Vote,
  VoteErrorKind,
  BucketSentiment,
  SentimentBreakdown,
  SentimentErrorKind,
} from "./types";
