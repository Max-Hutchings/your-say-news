import { useCallback, useEffect, useState } from "react";
import {
  approveUnwrappedStory,
  getUnwrappedAnalysisPosts,
  triggerUnwrappedGeneration,
  rejectUnwrappedStory,
  UnwrappedAdminApiError,
  getUnwrappedReviewQueue,
} from "../services/unwrappedAdminApi";
import type {
  UnwrappedGenerationTrigger,
  UnwrappedAdminPost,
  UnwrappedReviewError,
  UnwrappedReviewStory,
} from "../types";

export function useUnwrappedReviews() {
  const [reviews, setReviews] = useState<UnwrappedReviewStory[] | null>(null);
  const [error, setError] = useState<UnwrappedReviewError | null>(null);
  const [actingStoryId, setActingStoryId] = useState<string | null>(null);
  const [generatingPostId, setGeneratingPostId] = useState<number | null>(null);
  const [generationError, setGenerationError] = useState<UnwrappedReviewError | null>(null);
  const [posts, setPosts] = useState<UnwrappedAdminPost[] | null>(null);
  const [postsError, setPostsError] = useState<UnwrappedReviewError | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setReviews(await getUnwrappedReviewQueue());
    } catch (reason) {
      setError(toReviewError(reason));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const loadPosts = useCallback(async () => {
    setPostsError(null);
    try {
      setPosts(await getUnwrappedAnalysisPosts());
    } catch (reason) {
      setPostsError(toReviewError(reason));
    }
  }, []);

  useEffect(() => {
    void loadPosts();
  }, [loadPosts]);

  const act = useCallback(async (
    storyId: string,
    action: () => Promise<UnwrappedReviewStory>,
  ) => {
    setActingStoryId(storyId);
    setError(null);
    try {
      const saved = await action();
      setReviews((current) => current?.filter((story) => story.storyId !== saved.storyId) ?? null);
      return saved;
    } catch (reason) {
      setError(toReviewError(reason));
      throw reason;
    } finally {
      setActingStoryId(null);
    }
  }, []);

  const approve = useCallback(
    (storyId: string) => act(storyId, () => approveUnwrappedStory(storyId)),
    [act],
  );

  const reject = useCallback(
    (storyId: string, reason: string) =>
      act(storyId, () => rejectUnwrappedStory(storyId, reason)),
    [act],
  );

  const generate = useCallback(async (postId: number): Promise<UnwrappedGenerationTrigger> => {
    setGeneratingPostId(postId);
    setGenerationError(null);
    try {
      const trigger = await triggerUnwrappedGeneration(postId);
      await load();
      return trigger;
    } catch (reason) {
      setGenerationError(toReviewError(reason));
      throw reason;
    } finally {
      setGeneratingPostId(null);
    }
  }, [load]);

  return {
    reviews,
    error,
    actingStoryId,
    generatingPostId,
    generationError,
    posts,
    postsError,
    load,
    loadPosts,
    approve,
    reject,
    generate,
  };
}

function toReviewError(reason: unknown): UnwrappedReviewError {
  if (reason instanceof UnwrappedAdminApiError) {
    return { status: reason.status, message: reason.message };
  }
  return {
    status: null,
    message: reason instanceof Error ? reason.message : "The Unwrapped review request failed.",
  };
}
