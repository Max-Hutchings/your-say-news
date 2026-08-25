import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AutoPostRun } from "../types";

const api = vi.hoisted(() => ({
  getAutoPostRun: vi.fn(),
  getAutoPostRuns: vi.fn(),
  approveAutoPostRun: vi.fn(),
  selectAutoPostCandidate: vi.fn(),
  retryAutoPostDraft: vi.fn(),
  startAutoPostRun: vi.fn(),
  streamAutoPostRun: vi.fn(),
}));

vi.mock("../services/autoPostAdminApi", () => ({
  AutoPostAdminApiError: class AutoPostAdminApiError extends Error {
    constructor(public status: number, message: string) { super(message); }
  },
  ...api,
}));

import { useAutoPosts } from "./useAutoPosts";
import { AutoPostAdminApiError } from "../services/autoPostAdminApi";

const queuedRun: AutoPostRun = {
  id: "50b05ab6-a324-4fb4-bab6-e7c14bc5ce83",
  status: "QUEUED",
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
  updatedAt: "2026-08-20T12:00:00Z",
};

describe("useAutoPosts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.getAutoPostRuns.mockResolvedValue([]);
    api.getAutoPostRun.mockResolvedValue(queuedRun);
    api.streamAutoPostRun.mockImplementation(() => new Promise(() => undefined));
  });

  it("loads history, starts discovery, and applies streamed run updates", async () => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let sendEvent: ((event: { run: AutoPostRun }) => void) | undefined;
    let streamSignal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, onEvent, suppliedSignal) => {
      sendEvent = onEvent;
      streamSignal = suppliedSignal;
      return new Promise((_resolve, reject) => suppliedSignal.addEventListener(
        "abort",
        () => reject(new DOMException("Aborted", "AbortError")),
      ));
    });
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));

    await act(async () => result.current.create());
    expect(result.current.activeRun).toEqual(queuedRun);
    expect(api.streamAutoPostRun).toHaveBeenCalledWith(
      queuedRun.id,
      expect.any(Function),
      expect.any(AbortSignal),
    );

    const discoveringRun = { ...queuedRun, status: "DISCOVERING" as const };
    act(() => sendEvent?.({ run: discoveringRun }));
    expect(result.current.activeRun).toEqual(discoveringRun);
    expect(streamSignal?.aborted).toBe(false);

    const readyRun = { ...discoveringRun, status: "CANDIDATES_READY" as const };
    act(() => sendEvent?.({ run: readyRun }));
    await act(async () => Promise.resolve());
    expect(result.current.activeRun).toEqual(readyRun);
    expect(result.current.runs).toEqual([readyRun]);
    expect(streamSignal?.aborted).toBe(true);
    act(() => sendEvent?.({ run: queuedRun }));
    expect(result.current.activeRun).toEqual(readyRun);
    expect(result.current.error).toBeNull();
    expect(api.streamAutoPostRun).toHaveBeenCalledTimes(1);
    expect(api.getAutoPostRun).not.toHaveBeenCalled();
  });

  it("uses SSE without polling after a candidate is selected", async () => {
    const candidateId = "202d93e8-3d66-4a38-817d-cd11ab2de3c5";
    const draftId = "29d1a8e9-d432-4754-ac9e-b102a44eec13";
    const draftingRun: AutoPostRun = {
      ...queuedRun,
      status: "DRAFTING",
      selectedCandidateId: candidateId,
      pepperDraftId: draftId,
    };
    const readyRun: AutoPostRun = {
      ...draftingRun,
      status: "DRAFT_READY",
      draft: {
        id: draftId,
        summary: "European leaders pledged further support for Ukraine.",
        supportQuestion: "Should allies increase military support for Ukraine?",
        caseFor: "Supporters say stronger aid improves Ukraine's defence.",
        caseAgainst: "Critics warn that escalation could widen the conflict.",
        votingType: "BINARY",
        voteOptions: ["Agree", "Disagree"],
        citations: [{
          url: "https://example.org/world/ukraine-support",
          title: "Leaders discuss support for Ukraine",
          publisher: "Example News",
        }],
        version: 0,
      },
    };
    api.selectAutoPostCandidate.mockResolvedValue(draftingRun);
    let sendEvent: ((event: { run: AutoPostRun }) => void) | undefined;
    let streamSignal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, onEvent, suppliedSignal) => {
      sendEvent = onEvent;
      streamSignal = suppliedSignal;
      return new Promise(() => undefined);
    });
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));

    await act(async () => result.current.select(queuedRun.id, candidateId));
    act(() => sendEvent?.({ run: readyRun }));

    expect(api.selectAutoPostCandidate).toHaveBeenCalledWith(queuedRun.id, candidateId);
    expect(api.streamAutoPostRun).toHaveBeenCalledWith(
      queuedRun.id,
      expect.any(Function),
      expect.any(AbortSignal),
    );
    expect(result.current.activeRun).toEqual(readyRun);
    expect(streamSignal?.aborted).toBe(true);
    expect(api.getAutoPostRun).not.toHaveBeenCalled();
  });

  it("retries a failed draft and monitors the replacement job", async () => {
    const failedRun = {
      ...queuedRun,
      status: "FAILED" as const,
      selectedCandidateId: "candidate-1",
      pepperDraftId: "failed-draft-1",
      errorCode: "AUTO_POST_DRAFT_FAILED",
    };
    const retryingRun = {
      ...failedRun,
      status: "DRAFTING" as const,
      pepperDraftId: "retry-draft-2",
      errorCode: null,
      errorMessage: null,
    };
    api.retryAutoPostDraft.mockResolvedValue(retryingRun);
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));

    await act(async () => result.current.retry(failedRun.id));

    expect(api.retryAutoPostDraft).toHaveBeenCalledWith(failedRun.id);
    expect(api.streamAutoPostRun).toHaveBeenCalledWith(
      failedRun.id, expect.any(Function), expect.any(AbortSignal),
    );
    expect(result.current.activeRun).toEqual(retryingRun);
    expect(result.current.retryingRunId).toBeNull();
  });

  it.each([
    {
      ...queuedRun,
      status: "FAILED" as const,
      errorCode: "AUTO_POST_DRAFT_FAILED",
      errorMessage: "Post agent could not create the draft. Try a new run.",
    },
    {
      ...queuedRun,
      status: "PUBLISHED" as const,
      publishedPostId: 4102,
    },
  ])("stops the SSE stream when a run becomes $status", async (terminalRun) => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let sendEvent: ((event: { run: AutoPostRun }) => void) | undefined;
    let streamSignal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, onEvent, suppliedSignal) => {
      sendEvent = onEvent;
      streamSignal = suppliedSignal;
      return new Promise(() => undefined);
    });
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));
    await act(async () => result.current.create());

    act(() => sendEvent?.({ run: terminalRun }));

    expect(result.current.activeRun).toEqual(terminalRun);
    expect(streamSignal?.aborted).toBe(true);
    expect(api.getAutoPostRun).not.toHaveBeenCalled();
  });

  it("aborts an active authenticated stream when the desk unmounts", async () => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let signal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, _onEvent, suppliedSignal) => {
      signal = suppliedSignal;
      return new Promise(() => undefined);
    });
    const { result, unmount } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));
    await act(async () => result.current.create());

    unmount();

    expect(signal?.aborted).toBe(true);
  });

  it("shows an error when the SSE stream closes before the run finishes", async () => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let streamSignal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, _onEvent, suppliedSignal) => {
      streamSignal = suppliedSignal;
      return Promise.resolve();
    });
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));

    await act(async () => result.current.create());

    await waitFor(() => expect(result.current.error).toEqual({
      status: null,
      message: "The story discovery stream closed before the run finished.",
    }));
    expect(result.current.activeRun).toEqual(queuedRun);
    expect(streamSignal?.aborted).toBe(true);
    expect(api.getAutoPostRun).not.toHaveBeenCalled();
  });

  it("shows the authenticated API error when the SSE connection is rejected", async () => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let streamSignal: AbortSignal | undefined;
    api.streamAutoPostRun.mockImplementation((_id, _onEvent, suppliedSignal) => {
      streamSignal = suppliedSignal;
      return Promise.reject(
        new AutoPostAdminApiError(503, "The auto-post stream is unavailable."),
      );
    });
    const { result } = renderHook(() => useAutoPosts());
    await waitFor(() => expect(result.current.runs).toEqual([]));

    await act(async () => result.current.create());

    await waitFor(() => expect(result.current.error).toEqual({
      status: 503,
      message: "The auto-post stream is unavailable.",
    }));
    expect(result.current.activeRun).toEqual(queuedRun);
    expect(streamSignal?.aborted).toBe(true);
    expect(api.getAutoPostRun).not.toHaveBeenCalled();
  });
});
