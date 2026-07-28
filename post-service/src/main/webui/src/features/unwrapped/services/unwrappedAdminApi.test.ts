import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({
  getAccessToken: vi.fn().mockResolvedValue("admin-token"),
}));

import {
  approveUnwrappedStory,
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

    await approveUnwrappedStory("story-1");
    await rejectUnwrappedStory("story-1", "Needs a primary source.");

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
      }),
    );
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
