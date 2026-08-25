import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({ getAccessToken: vi.fn().mockResolvedValue("admin-auto-post-token") }));

import {
  approveAutoPostRun,
  getAutoPostRun,
  getAutoPostRuns,
  selectAutoPostCandidate,
  retryAutoPostDraft,
  startAutoPostRun,
  streamAutoPostRun,
} from "./autoPostAdminApi";

const run = {
  id: "50b05ab6-a324-4fb4-bab6-e7c14bc5ce83",
  status: "CANDIDATES_READY",
  windowStart: "2026-08-19T12:00:00Z",
  windowEnd: "2026-08-20T12:00:00Z",
  candidates: [],
  selectedCandidateId: null,
  pepperDraftId: null,
  draft: null,
  publishedPostId: null,
  errorCode: null,
  errorMessage: null,
  createdAt: "2026-08-20T12:00:00Z",
  updatedAt: "2026-08-20T12:01:00Z",
};
const candidateId = "4f864bb6-4e65-48fb-8e57-2e17cc5a869f";

describe("autoPostAdminApi", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("uses authenticated admin endpoints for history, polling, creation and selection", async () => {
    const pollingController = new AbortController();
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([run]), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(run), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...run, status: "QUEUED" }), { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...run, status: "DRAFTING" }), { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...run, status: "DRAFTING" }), { status: 202 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...run, status: "PUBLISHED" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getAutoPostRuns()).resolves.toEqual([run]);
    await expect(getAutoPostRun(run.id, pollingController.signal)).resolves.toEqual(run);
    await expect(startAutoPostRun()).resolves.toEqual({ ...run, status: "QUEUED" });
    await expect(selectAutoPostCandidate(run.id, candidateId))
      .resolves.toEqual({ ...run, status: "DRAFTING" });
    await expect(retryAutoPostDraft(run.id)).resolves.toEqual({ ...run, status: "DRAFTING" });
    await expect(approveAutoPostRun(run.id)).resolves.toEqual({ ...run, status: "PUBLISHED" });

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/admin/auto-post/runs", expect.objectContaining({
      headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      `/api/admin/auto-post/runs/${run.id}`,
      expect.objectContaining({
        signal: pollingController.signal,
        headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/admin/auto-post/runs", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      `/api/admin/auto-post/runs/${run.id}/candidates/${candidateId}/select`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      5,
      `/api/admin/auto-post/runs/${run.id}/retry-draft`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      6,
      `/api/admin/auto-post/runs/${run.id}/approve`,
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ Authorization: "Bearer admin-auto-post-token" }),
      }),
    );
  });

  it("parses split SSE frames and never puts the bearer token in the stream URL", async () => {
    const encoder = new TextEncoder();
    const first = JSON.stringify({ run: { ...run, status: "DISCOVERING" } });
    const second = JSON.stringify({ run });
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode(`data:${first.slice(0, 40)}`));
        controller.enqueue(encoder.encode(`${first.slice(40)}\n\ndata:${second}\n\n`));
        controller.close();
      },
    });
    const fetchMock = vi.fn().mockResolvedValue(new Response(body, {
      status: 200,
      headers: { "Content-Type": "text/event-stream" },
    }));
    vi.stubGlobal("fetch", fetchMock);
    const received: string[] = [];

    await streamAutoPostRun(run.id, (event) => received.push(event.run.status));

    expect(received).toEqual(["DISCOVERING", "CANDIDATES_READY"]);
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/admin/auto-post/runs/${run.id}/events`,
      expect.objectContaining({
        headers: expect.objectContaining({
          Accept: "text/event-stream",
          Authorization: "Bearer admin-auto-post-token",
        }),
      }),
    );
    expect(fetchMock.mock.calls[0][0]).not.toContain("admin-auto-post-token");
  });

  it("surfaces the server error message and status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: "Story discovery is unavailable." }),
      { status: 503, headers: { "Content-Type": "application/json" } },
    )));

    await expect(startAutoPostRun()).rejects.toMatchObject({
      status: 503,
      message: "Story discovery is unavailable.",
    });
  });

  it("rejects a successful SSE response that has no readable body", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body: null,
    }));

    await expect(streamAutoPostRun(run.id, vi.fn())).rejects.toMatchObject({
      status: 502,
      message: "The story discovery stream did not start.",
    });
  });
});
