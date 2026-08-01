import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({
  getAccessToken: vi.fn().mockResolvedValue("admin-token"),
}));

import {
  approveUnwrappedStory,
  getUnwrappedAnalysisPosts,
  triggerUnwrappedGeneration,
  getUnwrappedReviewQueue,
  rejectUnwrappedStory,
} from "./unwrappedAdminApi";

describe("unwrappedAdminApi", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the draft review queue with the admin token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("[]", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getUnwrappedReviewQueue()).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith("/admin/unwrapped/review", expect.objectContaining({
      headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
    }));
  });

  it("loads posts with their aggregate vote split", async () => {
    const posts = [{
      postId: 42,
      canonicalVoteCount: 125,
      overall: [
        { optionId: 71, label: "Agree", count: 75, percentage: 60 },
        { optionId: 72, label: "Disagree", count: 50, percentage: 40 },
      ],
    }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(posts), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getUnwrappedAnalysisPosts()).resolves.toEqual(posts);
    expect(fetchMock).toHaveBeenCalledWith(
      "/admin/unwrapped/posts?page=0&size=50",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
      }),
    );
  });

  it("sends explicit approve and reject actions", async () => {
    const story = { storyId: "story-1", status: "APPROVED" };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(story), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...story, status: "REJECTED" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(approveUnwrappedStory("story-1")).resolves.toEqual(story);
    await expect(rejectUnwrappedStory("story-1", "Needs a primary source.")).resolves.toEqual({
      ...story,
      status: "REJECTED",
    });

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/admin/unwrapped/story-1/approve",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/admin/unwrapped/story-1/reject",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ reason: "Needs a primary source." }),
        headers: expect.objectContaining({
          Authorization: "Bearer admin-token",
          "Content-Type": "application/json",
        }),
      }),
    );
  });

  it("preserves server and fallback errors", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ message: "Post 42 is unavailable." }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response("not-json", { status: 502 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(triggerUnwrappedGeneration(42)).rejects.toMatchObject({
      status: 409,
      message: "Post 42 is unavailable.",
    });
    await expect(getUnwrappedReviewQueue()).rejects.toMatchObject({
      status: 502,
      message: "The Unwrapped review request failed.",
    });
  });

  it("queues normal reconciliation for the selected post", async () => {
    const trigger = {
      postId: 42,
      status: "RECONCILIATION_QUEUED",
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(trigger), {
      status: 202,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(triggerUnwrappedGeneration(42)).resolves.toEqual(trigger);
    expect(fetchMock).toHaveBeenCalledWith(
      "/admin/unwrapped/posts/42/generate",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
      }),
    );
  });
});
