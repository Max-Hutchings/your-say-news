import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { UnwrappedAdminPost, UnwrappedReviewStory } from "../types";

vi.mock("../services/unwrappedAdminApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../services/unwrappedAdminApi")>();
  return {
    ...original,
    approveUnwrappedStory: vi.fn(),
    getUnwrappedAnalysisPosts: vi.fn(),
    getUnwrappedReviewQueue: vi.fn(),
    rejectUnwrappedStory: vi.fn(),
    triggerUnwrappedGeneration: vi.fn(),
  };
});

import {
  approveUnwrappedStory,
  getUnwrappedAnalysisPosts,
  getUnwrappedReviewQueue,
  triggerUnwrappedGeneration,
} from "../services/unwrappedAdminApi";
import { useUnwrappedReviews } from "./useUnwrappedReviews";

const review = {
  storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f",
  postId: 42,
  milestone: 100,
  canonicalVoteCount: 100,
  status: "DRAFT",
  generatedAt: "2026-07-28T10:00:00Z",
  draft: { pages: [], sources: [] },
} satisfies UnwrappedReviewStory;

const post = {
  postId: 42,
  summary: "A measured summary.",
  question: "Should the city introduce a workplace parking levy?",
  caseFor: null,
  caseAgainst: null,
  jurisdiction: "GLOBAL",
  votingType: "BINARY",
  createdAt: "2026-07-27T09:00:00Z",
  canonicalVoteCount: 100,
  overall: [],
} satisfies UnwrappedAdminPost;

describe("useUnwrappedReviews", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getUnwrappedReviewQueue).mockResolvedValue([review]);
    vi.mocked(getUnwrappedAnalysisPosts).mockResolvedValue([post]);
    vi.mocked(triggerUnwrappedGeneration).mockResolvedValue({
      postId: 42,
      status: "RECONCILIATION_QUEUED",
    });
    vi.mocked(approveUnwrappedStory).mockResolvedValue({ ...review, status: "APPROVED" });
  });

  it("loads posts and reviews, then reloads the review queue after generation", async () => {
    const { result } = renderHook(() => useUnwrappedReviews());

    await waitFor(() => {
      expect(result.current.reviews).toEqual([review]);
      expect(result.current.posts).toEqual([post]);
    });

    await act(async () => {
      await result.current.generate(42);
    });

    expect(triggerUnwrappedGeneration).toHaveBeenCalledWith(42);
    expect(getUnwrappedReviewQueue).toHaveBeenCalledTimes(2);
    expect(result.current.generatingPostId).toBeNull();
    expect(result.current.generationError).toBeNull();
  });

  it("removes an approved draft and clears the acting state", async () => {
    const { result } = renderHook(() => useUnwrappedReviews());
    await waitFor(() => expect(result.current.reviews).toEqual([review]));

    await act(async () => {
      await result.current.approve(review.storyId);
    });

    expect(approveUnwrappedStory).toHaveBeenCalledWith(review.storyId);
    expect(result.current.reviews).toEqual([]);
    expect(result.current.actingStoryId).toBeNull();
  });
});
