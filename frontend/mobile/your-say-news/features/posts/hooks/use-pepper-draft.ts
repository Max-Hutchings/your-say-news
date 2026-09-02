import { useCallback, useEffect, useRef, useState } from "react";
import {
  getActivePepperGeneration,
  getLatestPepperDraft,
  reconnectPepperGeneration,
  savePepperDraft,
  streamPepperGeneration,
} from "../services/PepperAgentService";
import type {
  PepperDraftRecord,
  PepperDraftStatus,
  PepperGenerationEvent,
  PepperPostDraft,
} from "../types";

const SAFE_FAILURE = "Pepper AI is having trouble, please try again later.";
const AUTOSAVE_DELAY_MS = 500;

export type PepperComposeStatus = "IDLE" | PepperDraftStatus;

export function usePepperDraft() {
  const [draft, setDraft] = useState<PepperDraftRecord | null>(null);
  const [status, setStatus] = useState<PepperComposeStatus>("IDLE");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const autosaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const saveInFlight = useRef(false);
  const pendingContent = useRef<PepperPostDraft | null>(null);
  const pendingReady = useRef(false);
  const flushAutosaveRef = useRef<() => void>(() => undefined);
  const draftRef = useRef<PepperDraftRecord | null>(null);

  const adoptDraft = useCallback((next: PepperDraftRecord | null) => {
    draftRef.current = next;
    setDraft(next);
    setStatus(next?.status ?? "IDLE");
    setError(next?.status === "FAILED" ? SAFE_FAILURE : null);
  }, []);

  const applyEvent = useCallback((event: PepperGenerationEvent, prompt = "") => {
    setStatus(event.status);
    setDraft((current) => {
      const next: PepperDraftRecord = {
        id: event.draftId,
        prompt: current?.prompt || prompt,
        replicaId: event.replicaId,
        status: event.status,
        success: event.status === "FINISHED" ? true : event.status === "FAILED" ? false : null,
        content: event.result ?? current?.content ?? null,
        errorMessage: event.status === "FAILED" ? SAFE_FAILURE : null,
        publishedPostId: current?.publishedPostId ?? null,
        version: event.status === "FINISHED" ? Math.max(1, current?.version ?? 0) : current?.version ?? 0,
      };
      draftRef.current = next;
      return next;
    });
    if (event.status === "FAILED") setError(SAFE_FAILURE);
  }, []);

  useEffect(() => {
    let mounted = true;
    void (async () => {
      try {
        const [active, latest] = await Promise.all([
          getActivePepperGeneration(),
          getLatestPepperDraft(),
        ]);
        if (!mounted) return;
        adoptDraft(latest);
        if (active && latest?.status !== "FINISHED" && latest?.status !== "FAILED") {
          await reconnectPepperGeneration(
            active.draftId,
            active.replicaId,
            (event) => mounted && applyEvent(event, latest?.prompt ?? ""),
          );
        }
      } catch {
        if (mounted) {
          setStatus("FAILED");
          setError(SAFE_FAILURE);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
      if (autosaveTimer.current) clearTimeout(autosaveTimer.current);
    };
  }, [adoptDraft, applyEvent]);

  const generate = useCallback(async (prompt: string) => {
    const request = prompt.trim();
    if (!request) return;
    setLoading(true);
    setError(null);
    try {
      await streamPepperGeneration(request, (event) => applyEvent(event, request));
      const latest = await getLatestPepperDraft();
      if (latest) adoptDraft(latest);
    } catch {
      setStatus("FAILED");
      setError(SAFE_FAILURE);
    } finally {
      setLoading(false);
    }
  }, [adoptDraft, applyEvent]);

  const flushAutosave = useCallback(() => {
    if (saveInFlight.current || !pendingReady.current) return;
    const current = draftRef.current;
    const content = pendingContent.current;
    if (!current || current.status !== "FINISHED" || !content) return;
    const draftId = current.id;
    pendingReady.current = false;
    saveInFlight.current = true;
    void savePepperDraft(draftId, content, current.version)
      .then((saved) => {
        const newest = draftRef.current;
        if (!newest || newest.id !== draftId) return;
        const hasNewerEdit = pendingContent.current !== content;
        const next = hasNewerEdit
          ? { ...newest, version: saved.version }
          : saved;
        if (!hasNewerEdit) pendingContent.current = null;
        draftRef.current = next;
        setDraft(next);
      })
      .catch(() => setError(SAFE_FAILURE))
      .finally(() => {
        saveInFlight.current = false;
        if (pendingReady.current) flushAutosaveRef.current();
      });
  }, []);
  flushAutosaveRef.current = flushAutosave;

  const changeDraft = useCallback((content: PepperPostDraft) => {
    const current = draftRef.current;
    if (!current || current.status !== "FINISHED") return;
    const local = { ...current, content };
    draftRef.current = local;
    setDraft(local);
    if (autosaveTimer.current) clearTimeout(autosaveTimer.current);
    pendingContent.current = content;
    pendingReady.current = false;
    autosaveTimer.current = setTimeout(() => {
      pendingReady.current = true;
      flushAutosaveRef.current();
    }, AUTOSAVE_DELAY_MS);
  }, []);

  return { draft, status, loading, error, generate, changeDraft };
}
