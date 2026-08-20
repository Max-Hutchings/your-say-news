import { useCallback, useEffect, useRef, useState } from "react";
import {
  approveAutoPostRun,
  AutoPostAdminApiError,
  getAutoPostRuns,
  selectAutoPostCandidate,
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
  const streams = useRef(new Map<string, AbortController>());

  const replaceRun = useCallback((run: AutoPostRun) => {
    setRuns((current) => current === null
      ? [run]
      : [run, ...current.filter((candidate) => candidate.id !== run.id)]);
    setActiveRun(run);
  }, []);

  const load = useCallback(async () => {
    setError(null);
    try {
      const loaded = await getAutoPostRuns();
      setRuns(loaded);
      setActiveRun((current) => current
        ? loaded.find((run) => run.id === current.id) ?? current
        : loaded.find((run) => !["PUBLISHED", "FAILED"].includes(run.status)) ?? null);
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
    void streamAutoPostRun(runId, (event) => replaceRun(event.run), controller.signal)
      .catch((reason) => {
        if (!controller.signal.aborted) {
          setError(toAutoPostError(reason));
        }
      })
      .finally(() => streams.current.delete(runId));
  }, [replaceRun]);

  const create = useCallback(async () => {
    setCreating(true);
    setError(null);
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

  return { runs, activeRun, error, creating, selectingCandidateId, approving, load, create, select, approve };
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
