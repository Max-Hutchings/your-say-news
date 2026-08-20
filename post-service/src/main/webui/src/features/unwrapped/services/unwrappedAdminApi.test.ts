import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({
  getAccessToken: vi.fn().mockResolvedValue("admin-token"),
}));

import {
  approveUnwrappedStory,
  getUnwrappedAnalysisPosts,
  getUnwrappedGenerationMonitor,
  getUnwrappedBenchmarkPrompt,
  generateUnwrappedBenchmark,
  triggerUnwrappedGeneration,
  getUnwrappedReviewQueue,
  rejectUnwrappedStory,
  UnwrappedAdminApiError,
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
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/unwrapped/review", expect.objectContaining({
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
      "/api/admin/unwrapped/posts?page=0&size=50",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
      }),
    );
  });

  it("loads persistent generation progress", async () => {
    const monitor = {
      workerAvailable: false,
      refreshedAt: "2026-07-28T10:01:00Z",
      statuses: [{ postId: 42, state: "QUEUED", queuedJobs: 1 }],
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(monitor), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getUnwrappedGenerationMonitor()).resolves.toEqual(monitor);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/admin/unwrapped/generation-status",
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
      "/api/admin/unwrapped/story-1/approve",
      expect.objectContaining({ method: "POST" }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/admin/unwrapped/story-1/reject",
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
      "/api/admin/unwrapped/posts/42/generate",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
      }),
    );
  });

  it("loads the production prompt and submits one benchmark replacement", async () => {
    const prompt = {
      systemPrompt: "Production system prompt",
      outputInstructions: "Return exactly two pages.",
      input: { postId: 42, options: [] },
    };
    const benchmark = {
      postId: 42,
      generatedAt: "2026-08-13T18:18:18Z",
      options: [
        { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
        { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
      ],
      variants: [{
        position: 1,
        systemPrompt: "Prompt A",
        effectiveSystemPrompt: "Prompt A",
        attemptCount: 1,
        status: "SUCCEEDED",
        model: "grok-4.5",
        providerResponseId: "response-42",
        argumentPages: [{
          optionId: 71,
          headline: "Young commuters gain a cheaper route through the city",
          selectedCohortIds: ["ageRange=AGE_25_34"],
          paragraphs: [{
            text: "Reliable buses can give younger commuters a practical alternative to paying the levy.",
            sourceIds: ["source-1"],
          }],
          caveat: "Aggregate voting cannot identify individual motivation.",
          sources: [],
        }, {
          optionId: 72,
          headline: "Night workers cannot depend on buses that have stopped running",
          selectedCohortIds: [],
          paragraphs: [{
            text: "Late shifts can make driving the only realistic journey home.",
            sourceIds: ["source-2"],
          }],
          caveat: "Aggregate voting cannot identify individual motivation.",
          sources: [],
        }],
        errorCode: null,
        errorMessage: null,
      }],
    };
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(prompt), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify(benchmark), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getUnwrappedBenchmarkPrompt(42)).resolves.toEqual(prompt);
    await expect(generateUnwrappedBenchmark(42, ["Prompt A"]))
      .resolves.toEqual(benchmark);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/api/admin/unwrapped/posts/42/benchmark/context",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer admin-token" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/admin/unwrapped/posts/42/benchmark",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ systemPrompts: ["Prompt A"] }),
        headers: expect.objectContaining({
          Authorization: "Bearer admin-token",
          "Content-Type": "application/json",
        }),
      }),
    );
  });

  it("rejects a successful benchmark response when an option article has no content", async () => {
    const incompleteBenchmark = {
      postId: 42,
      generatedAt: "2026-08-13T18:18:18Z",
      options: [
        { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
        { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
      ],
      variants: [{
        position: 1,
        status: "SUCCEEDED",
        argumentPages: [{
          optionId: 71,
          headline: "A complete article",
          paragraphs: [{ text: "A complete paragraph.", sourceIds: [] }],
          sources: [],
        }],
      }],
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify(incompleteBenchmark),
      { status: 200, headers: { "Content-Type": "application/json" } },
    )));

    const failure = await generateUnwrappedBenchmark(42, ["Prompt A"])
      .then(() => null, (reason: unknown) => reason);

    expect(failure).toBeInstanceOf(UnwrappedAdminApiError);
    expect(failure).toMatchObject({
      status: 502,
      message: "The model returned incomplete article content for Disagree.",
    });
  });

  it.each([
    ["a blank headline", {
      optionId: 71,
      headline: "   ",
      paragraphs: [{ text: "A complete paragraph.", sourceIds: [] }],
      sources: [],
    }],
    ["no paragraphs", {
      optionId: 71,
      headline: "A complete headline",
      paragraphs: [],
      sources: [],
    }],
    ["a blank paragraph", {
      optionId: 71,
      headline: "A complete headline",
      paragraphs: [{ text: "   ", sourceIds: [] }],
      sources: [],
    }],
  ])("rejects a successful benchmark response containing %s", async (_case, page) => {
    const incompleteBenchmark = {
      postId: 42,
      generatedAt: "2026-08-13T18:18:18Z",
      options: [{ id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" }],
      variants: [{
        position: 1,
        status: "SUCCEEDED",
        argumentPages: [page],
      }],
    };
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify(incompleteBenchmark),
      { status: 200, headers: { "Content-Type": "application/json" } },
    )));

    await expect(generateUnwrappedBenchmark(42, ["Prompt A"]))
      .rejects.toMatchObject({
        status: 502,
        message: "The model returned incomplete article content for Agree.",
      });
  });
});
