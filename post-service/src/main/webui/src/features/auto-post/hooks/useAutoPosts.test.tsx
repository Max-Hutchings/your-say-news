import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AutoPostRun } from "../types";

const api = vi.hoisted(() => ({
  getAutoPostRuns: vi.fn(),
  approveAutoPostRun: vi.fn(),
  selectAutoPostCandidate: vi.fn(),
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
    api.streamAutoPostRun.mockImplementation(() => new Promise(() => undefined));
  });

  it("loads history, starts discovery, and applies streamed run updates", async () => {
    api.startAutoPostRun.mockResolvedValue(queuedRun);
    let sendEvent: ((event: { run: AutoPostRun }) => void) | undefined;
    api.streamAutoPostRun.mockImplementation((_id, onEvent) => {
      sendEvent = onEvent;
      return new Promise(() => undefined);
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

    const readyRun = { ...queuedRun, status: "CANDIDATES_READY" as const };
    act(() => sendEvent?.({ run: readyRun }));
    expect(result.current.activeRun).toEqual(readyRun);
    expect(result.current.runs).toEqual([readyRun]);
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
});
