import { getAccessToken } from "../../auth";
import type {
  UnwrappedAdminPost,
  UnwrappedGenerationTrigger,
  UnwrappedGenerationMonitor,
  UnwrappedReviewStory,
} from "../types";

export class UnwrappedAdminApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function unwrappedRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getAccessToken();
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new UnwrappedAdminApiError(
      response.status,
      body?.message ?? "The Unwrapped review request failed.",
    );
  }

  return response.json() as Promise<T>;
}

export function getUnwrappedReviewQueue(): Promise<UnwrappedReviewStory[]> {
  return unwrappedRequest<UnwrappedReviewStory[]>("/api/admin/unwrapped/review");
}

export function getUnwrappedAnalysisPosts(): Promise<UnwrappedAdminPost[]> {
  return unwrappedRequest<UnwrappedAdminPost[]>("/api/admin/unwrapped/posts?page=0&size=50");
}

export function getUnwrappedGenerationMonitor(): Promise<UnwrappedGenerationMonitor> {
  return unwrappedRequest<UnwrappedGenerationMonitor>("/api/admin/unwrapped/generation-status");
}

export function triggerUnwrappedGeneration(postId: number): Promise<UnwrappedGenerationTrigger> {
  return unwrappedRequest<UnwrappedGenerationTrigger>(`/api/admin/unwrapped/posts/${postId}/generate`, {
    method: "POST",
  });
}

export function approveUnwrappedStory(storyId: string): Promise<UnwrappedReviewStory> {
  return unwrappedRequest<UnwrappedReviewStory>(`/api/admin/unwrapped/${storyId}/approve`, {
    method: "POST",
  });
}

export function rejectUnwrappedStory(
  storyId: string,
  reason: string,
): Promise<UnwrappedReviewStory> {
  return unwrappedRequest<UnwrappedReviewStory>(`/api/admin/unwrapped/${storyId}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
