import { useCallback, useEffect, useRef, useState } from "react";
import { getUnwrapped, submitFollowUp } from "../services/UnwrappedService";
import type { UnwrappedResponse } from "../types";

export function useUnwrapped(postId: number) {
  const [data, setData] = useState<UnwrappedResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(false);
  const followUpInFlight = useRef<number | null>(null);
  const activePostId = useRef(postId);
  const refreshSequence = useRef(0);
  activePostId.current = postId;

  const refresh = useCallback(async () => {
    const requestedPostId = postId;
    const requestId = ++refreshSequence.current;
    setLoading(true);
    setError(false);
    try {
      const response = await getUnwrapped(requestedPostId);
      if (activePostId.current === requestedPostId
          && refreshSequence.current === requestId) {
        setData(response);
      }
    } catch {
      if (activePostId.current === requestedPostId
          && refreshSequence.current === requestId) {
        setError(true);
      }
    } finally {
      if (activePostId.current === requestedPostId
          && refreshSequence.current === requestId) {
        setLoading(false);
      }
    }
  }, [postId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    followUpInFlight.current = null;
    setSubmitting(false);
  }, [postId]);

  const followUp = useCallback(async (storyId: string, optionId: number) => {
    if (followUpInFlight.current === postId) return false;
    const requestedPostId = postId;
    followUpInFlight.current = requestedPostId;
    setSubmitting(true);
    setError(false);
    try {
      const response = await submitFollowUp(requestedPostId, storyId, optionId);
      if (activePostId.current !== requestedPostId) return false;
      setData((current) => current
        ? { ...current, existingFollowUpOptionId: response.optionId }
        : current);
      return true;
    } catch {
      if (activePostId.current === requestedPostId) setError(true);
      return false;
    } finally {
      if (followUpInFlight.current === requestedPostId) {
        followUpInFlight.current = null;
      }
      if (activePostId.current === requestedPostId) setSubmitting(false);
    }
  }, [postId]);

  return { data, loading, submitting, error, refresh, followUp };
}
