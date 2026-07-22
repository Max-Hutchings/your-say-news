import { useCallback, useEffect, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { castVote as castVoteRequest, getMyVote } from "../services/VoteService";
import type { VoteErrorKind } from "../types";

export interface VoteState {
  loading: boolean;
  myVote: number | null;
  locked: boolean;
  submitting: boolean;
  error: VoteErrorKind | null;
  vote: (optionId: number) => Promise<boolean>;
}

function classifyError(err: unknown): VoteErrorKind {
  if (isAxiosError(err)) {
    if (!err.response) return "network";
    if (err.response.status === 409) return "duplicate";
    if (err.response.status === 401 || err.response.status === 403) return "auth";
  }
  return "unknown";
}

export function useVote(postId: number): VoteState {
  const [loading, setLoading] = useState(true);
  const [myVote, setMyVote] = useState<number | null>(null);
  const [locked, setLocked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<VoteErrorKind | null>(null);
  const mounted = useRef(true);
  const submittingRef = useRef(false);

  useEffect(() => {
    mounted.current = true;
    getMyVote(postId)
      .then((existing) => {
        if (!mounted.current) return;
        setMyVote(existing?.optionId ?? null);
        setLocked(Boolean(existing));
      })
      .catch(() => undefined)
      .finally(() => {
        if (mounted.current) setLoading(false);
      });
    return () => { mounted.current = false; };
  }, [postId]);

  const vote = useCallback(async (optionId: number) => {
    if (submittingRef.current || locked) return false;
    submittingRef.current = true;
    setSubmitting(true);
    setError(null);
    try {
      const created = await castVoteRequest(postId, optionId);
      if (mounted.current) {
        setMyVote(created.optionId);
        setLocked(true);
      }
      return true;
    } catch (err) {
      const kind = classifyError(err);
      if (kind === "duplicate") {
        let existing = null;
        try { existing = await getMyVote(postId); } catch { /* neutral locked state below */ }
        if (mounted.current) {
          setMyVote(existing?.optionId ?? null);
          setLocked(true);
        }
        return true;
      }
      if (mounted.current) setError(kind);
      return false;
    } finally {
      submittingRef.current = false;
      if (mounted.current) setSubmitting(false);
    }
  }, [locked, postId]);

  return { loading, myVote, locked, submitting, error, vote };
}
