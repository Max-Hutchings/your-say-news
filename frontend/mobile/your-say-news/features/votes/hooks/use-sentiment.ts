import { useCallback, useEffect, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { getAxisSentiment, getOverallSentiment } from "../services/SentimentService";
import type { SentimentBreakdown, SentimentErrorKind } from "../types";

/**
 * Owns the results state for one post + the currently selected characteristic axis.
 *
 * The overall split is fetched once per post; the axis breakdown refetches whenever `axis`
 * changes, so switching the selector re-renders the bars with the new group's numbers. `loading`
 * tracks the axis fetch (the part that changes on a switch) and stays true until the first bars
 * are ready. Failures surface as a classified `error`; a 403 (`notVoted`) shouldn't normally
 * happen since results only open after voting.
 */
export interface SentimentState {
  /** Overall yes/no split for the post; null until loaded. */
  overall: SentimentBreakdown | null;
  /** The split for the selected axis; null until loaded. */
  breakdown: SentimentBreakdown | null;
  /** True while the axis breakdown is being (re)fetched. */
  loading: boolean;
  /** The last fetch failure, or null. */
  error: SentimentErrorKind | null;
  /** Refetch overall + the current axis (e.g. from an error-state retry). */
  retry: () => void;
}

function classifyError(err: unknown): SentimentErrorKind {
  if (isAxiosError(err)) {
    if (!err.response) return "network";
    if (err.response.status === 403) return "notVoted";
    if (err.response.status === 401) return "auth";
  }
  return "unknown";
}

export function useSentiment(postId: number, axis: string): SentimentState {
  const [overall, setOverall] = useState<SentimentBreakdown | null>(null);
  const [breakdown, setBreakdown] = useState<SentimentBreakdown | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<SentimentErrorKind | null>(null);
  const [nonce, setNonce] = useState(0);

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // Overall — constant across axis switches, so keyed only on the post (and manual retry).
  useEffect(() => {
    getOverallSentiment(postId)
      .then((data) => {
        if (mounted.current) setOverall(data);
      })
      .catch((err) => {
        if (mounted.current) setError(classifyError(err));
      });
  }, [postId, nonce]);

  // Breakdown — refetches on every axis change. Clears prior bars so we never show a stale group.
  useEffect(() => {
    setLoading(true);
    setBreakdown(null);
    setError(null);
    getAxisSentiment(postId, axis)
      .then((data) => {
        if (mounted.current) setBreakdown(data);
      })
      .catch((err) => {
        if (mounted.current) setError(classifyError(err));
      })
      .finally(() => {
        if (mounted.current) setLoading(false);
      });
  }, [postId, axis, nonce]);

  const retry = useCallback(() => setNonce((n) => n + 1), []);

  return { overall, breakdown, loading, error, retry };
}
