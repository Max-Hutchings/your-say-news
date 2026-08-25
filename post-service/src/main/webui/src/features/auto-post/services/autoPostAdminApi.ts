import { getAccessToken } from "../../auth";
import type { AutoPostEvent, AutoPostRun } from "../types";

export class AutoPostAdminApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function autoPostRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getAccessToken();
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...init?.headers,
    },
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  return response.json() as Promise<T>;
}

export function getAutoPostRuns(): Promise<AutoPostRun[]> {
  return autoPostRequest<AutoPostRun[]>("/api/admin/auto-post/runs");
}

export function getAutoPostRun(runId: string, signal?: AbortSignal): Promise<AutoPostRun> {
  return autoPostRequest<AutoPostRun>(`/api/admin/auto-post/runs/${runId}`, { signal });
}

export function startAutoPostRun(): Promise<AutoPostRun> {
  return autoPostRequest<AutoPostRun>("/api/admin/auto-post/runs", { method: "POST" });
}

export function selectAutoPostCandidate(runId: string, candidateId: string): Promise<AutoPostRun> {
  return autoPostRequest<AutoPostRun>(
    `/api/admin/auto-post/runs/${runId}/candidates/${candidateId}/select`,
    { method: "POST" },
  );
}

export function retryAutoPostDraft(runId: string): Promise<AutoPostRun> {
  return autoPostRequest<AutoPostRun>(
    `/api/admin/auto-post/runs/${runId}/retry-draft`,
    { method: "POST" },
  );
}

export function approveAutoPostRun(runId: string): Promise<AutoPostRun> {
  return autoPostRequest<AutoPostRun>(`/api/admin/auto-post/runs/${runId}/approve`, { method: "POST" });
}

export async function streamAutoPostRun(
  runId: string,
  onEvent: (event: AutoPostEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = await getAccessToken();
  const response = await fetch(`/api/admin/auto-post/runs/${runId}/events`, {
    headers: {
      Accept: "text/event-stream",
      Authorization: `Bearer ${token}`,
    },
    signal,
  });
  if (!response.ok) {
    throw await responseError(response);
  }
  if (!response.body) {
    throw new AutoPostAdminApiError(502, "The story discovery stream did not start.");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "";
  while (true) {
    const { done, value } = await reader.read();
    pending += decoder.decode(value, { stream: !done });
    const frames = pending.split(/\r?\n\r?\n/);
    pending = frames.pop() ?? "";
    frames.forEach((frame) => emitFrame(frame, onEvent));
    if (done) {
      if (pending.trim()) {
        emitFrame(pending, onEvent);
      }
      return;
    }
  }
}

function emitFrame(frame: string, onEvent: (event: AutoPostEvent) => void) {
  const data = frame.split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
  if (data) {
    onEvent(JSON.parse(data) as AutoPostEvent);
  }
}

async function responseError(response: Response): Promise<AutoPostAdminApiError> {
  const body = await response.json().catch(() => null) as { message?: string } | null;
  return new AutoPostAdminApiError(
    response.status,
    body?.message ?? "The official-post request failed.",
  );
}
