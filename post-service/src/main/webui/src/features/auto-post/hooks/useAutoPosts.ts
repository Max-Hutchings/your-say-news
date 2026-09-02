import { useCallback, useEffect, useRef, useState } from "react";
import {
  approveAutoPostRun,
  AutoPostAdminApiError,
  getAutoPostRun,
  getAutoPostRuns,
  selectAutoPostCandidate,
  retryAutoPostDraft,
  startAutoPostRun,
  streamAutoPostRun,
} from "../services/autoPostAdminApi";
import type { AutoPostError, AutoPostRun } from "../types";

export function useAutoPosts() {
  const [runs, setRuns] = useState<AutoPostRun[] | null>(null);
  const [activeRun, setActiveRun] = useState<AutoPostRun | null>(null);
  const [error, setError] = useState<AutoPostError | null>(null);
  const [creating, setCreating] = useState(false);
  const [selectingCandidateId, setSelectingCandidateId] = useState<string | null>(null);
  const [approving, setApproving] = useState(false);
  const [retryingRunId, setRetryingRunId] = useState<string | null>(null);
  const [publishedPostId, setPublishedPostId] = useState<number | null>(null);
  const [viewedRun, setViewedRun] = useState<AutoPostRun | null>(null);
  const [loadingRunId, setLoadingRunId] = useState<string | null>(null);
  const streams = useRef(new Map<string, AbortController>());

  const replaceRun = useCallback((run: AutoPostRun) => {
    setRuns((current) => current === null
      ? [run]
      : [run, ...current.filter((candidate) => candidate.id !== run.id)]);
    if (run.status === "PUBLISHED") {
      setActiveRun(null);
      setPublishedPostId(run.publishedPostId);
    } else {
      setActiveRun(run);
    }
  }, []);

  const load = useCallback(async () => {
    setError(null);
    try {
      const loaded = await getAutoPostRuns();
      setRuns(loaded);
      setActiveRun((current) => {
        const currentRun = current
          ? loaded.find((run) => run.id === current.id)
          : null;
        return currentRun && currentRun.status !== "PUBLISHED"
          ? currentRun
          : loaded.find((run) => !["PUBLISHED", "FAILED"].includes(run.status)) ?? null;
      });
    } catch (reason) {
      setError(toAutoPostError(reason));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => () => {
    streams.current.forEach((controller) => controller.abort());
    streams.current.clear();
  }, []);

  const monitor = useCallback((runId: string) => {
    streams.current.get(runId)?.abort();
    const controller = new AbortController();
    streams.current.set(runId, controller);
    let terminalRunSeen = false;
    const applyRun = (run: AutoPostRun) => {
      if (terminalRunSeen) return;
      replaceRun(run);
      if (isTerminal(run)) {
        terminalRunSeen = true;
        controller.abort();
      }
    };
    void streamAutoPostRun(
      runId,
      (event) => applyRun(event.run),
      controller.signal,
    ).then(() => {
      if (!terminalRunSeen) {
        throw new Error("The story discovery stream closed before the run finished.");
      }
    }).catch((reason) => {
      if (!terminalRunSeen && !controller.signal.aborted) {
        setError(toAutoPostError(reason));
      }
    })
      .finally(() => {
        controller.abort();
        if (streams.current.get(runId) === controller) {
          streams.current.delete(runId);
        }
      });
  }, [replaceRun]);

  const create = useCallback(async () => {
    setCreating(true);
    setError(null);
    setPublishedPostId(null);
    setViewedRun(null);
    try {
      const run = await startAutoPostRun();
      replaceRun(run);
      monitor(run.id);
    } catch (reason) {
      setError(toAutoPostError(reason));
      throw reason;
    } finally {
      setCreating(false);
    }
  }, [monitor, replaceRun]);

  const select = useCallback(async (runId: string, candidateId: string) => {
    setSelectingCandidateId(candidateId);
    setError(null);
    try {
      replaceRun(await selectAutoPostCandidate(runId, candidateId));
      monitor(runId);
    } catch (reason) {
      setError(toAutoPostError(reason));
      throw reason;
    } finally {
      setSelectingCandidateId(null);
    }
  }, [monitor, replaceRun]);

  const approve = useCallback(async (runId: string) => {
    setApproving(true);
    setError(null);
    try {
      replaceRun(await approveAutoPostRun(runId));
    } catch (reason) {
      setError(toAutoPostError(reason));
      throw reason;
    } finally {
      setApproving(false);
    }
  }, [replaceRun]);

  const retry = useCallback(async (runId: string) => {
    setRetryingRunId(runId);
    setError(null);
    try {
      replaceRun(await retryAutoPostDraft(runId));
      monitor(runId);
    } catch (reason) {
      setError(toAutoPostError(reason));
      throw reason;
    } finally {
      setRetryingRunId(null);
    }
  }, [monitor, replaceRun]);

  const viewRun = useCallback(async (runId: string) => {
    setLoadingRunId(runId);
    setError(null);
    try {
      setViewedRun(await getAutoPostRun(runId));
    } catch (reason) {
      setError(toAutoPostError(reason));
    } finally {
      setLoadingRunId(null);
    }
  }, []);

  return {
    runs, activeRun, viewedRun, error, creating, selectingCandidateId, approving,
    retryingRunId, publishedPostId, loadingRunId,
    load, create, select, approve, retry, viewRun,
  };
}

function isTerminal(run: AutoPostRun): boolean {
  return ["CANDIDATES_READY", "DRAFT_READY", "FAILED", "PUBLISHED"].includes(run.status);
}

function toAutoPostError(reason: unknown): AutoPostError {
  if (reason instanceof AutoPostAdminApiError) {
    return { status: reason.status, message: reason.message };
  }
  return {
    status: null,
    message: reason instanceof Error ? reason.message : "The official-post request failed.",
  };
}
