import { useCallback, useEffect, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { castVote as castVoteRequest, getMyVote } from "../services/VoteService";
import type { VoteErrorKind } from "../types";

/**
 * Owns one post's vote state for the feed: whether the caller has already voted (fetched on
 * mount from `GET /votes/{postId}/mine`), and the cast itself.
 *
 * MVP1 rule: a vote is LOCKED after the first cast — `myVote` flips from null to the chosen
 * stance and further presses are ignored, matching the backend's 409 duplicate-vote guard. A
 * 409 (someone raced us, or stale state) is not shown as an error: we reconcile to the stored
 * stance and lock. Auth/network/unknown failures surface as `error` and leave the post votable
 * so the user can retry.
 */
export interface VoteState {
  /** Loading the caller's existing vote for this post (initial mount). */
  loading: boolean;
  /** The caller's stance once they've voted (true = agree, false = disagree); null if not yet. */
  myVote: boolean | null;
  /** True while a cast is in flight. */
  submitting: boolean;
  /** The last non-duplicate failure, cleared when a new cast starts; null otherwise. */
  error: VoteErrorKind | null;
  /** Cast a vote. No-op once locked or while submitting. */
  vote: (voteFor: boolean) => Promise<void>;
}

function classifyError(err: unknown): VoteErrorKind {
  if (isAxiosError(err)) {
    if (!err.response) return "network";
    const status = err.response.status;
    if (status === 409) return "duplicate";
    if (status === 401 || status === 403) return "auth";
  }
  return "unknown";
}

export function useVote(postId: number): VoteState {
  const [loading, setLoading] = useState(true);
  const [myVote, setMyVote] = useState<boolean | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<VoteErrorKind | null>(null);

  // Guards state writes after unmount — feed cards mount/unmount as the reader swipes.
  const mounted = useRef(true);

  // Fetch the caller's existing vote once on mount. `loading` starts true; a feed card's postId
  // is stable for its lifetime, so there's no need to reset state synchronously here.
  useEffect(() => {
    mounted.current = true;
    getMyVote(postId)
      .then((existing) => {
        if (mounted.current) setMyVote(existing ? existing.voteFor : null);
      })
      .catch(() => {
        // A failed status lookup should not block voting — treat as "not voted yet".
      })
      .finally(() => {
        if (mounted.current) setLoading(false);
      });
    return () => {
      mounted.current = false;
    };
  }, [postId]);

  const vote = useCallback(
    async (voteFor: boolean) => {
      // Locked after the first vote, and never fire two casts at once.
      if (submitting || myVote !== null) return;
      setSubmitting(true);
      setError(null);
      try {
        const created = await castVoteRequest(postId, voteFor);
        if (mounted.current) setMyVote(created.voteFor);
      } catch (err) {
        const kind = classifyError(err);
        if (kind === "duplicate") {
          // Already voted — reconcile to the stored stance (falling back to the attempted one)
          // and lock, without surfacing an error.
          let stance = voteFor;
          try {
            const existing = await getMyVote(postId);
            if (existing) stance = existing.voteFor;
          } catch {
            // Keep the attempted stance if the reconciling lookup also fails.
          }
          if (mounted.current) setMyVote(stance);
        } else if (mounted.current) {
          setError(kind);
        }
      } finally {
        if (mounted.current) setSubmitting(false);
      }
    },
    [postId, submitting, myVote]
  );

  return { loading, myVote, submitting, error, vote };
}
