import { getAccessToken } from "../../auth";
import type { ForcedUnwrappedJob, UnwrappedReviewStory } from "../types";

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
  return unwrappedRequest<UnwrappedReviewStory[]>("/admin/unwrapped/review");
}

export function forceUnwrappedGeneration(postId: number): Promise<ForcedUnwrappedJob> {
  return unwrappedRequest<ForcedUnwrappedJob>(`/admin/unwrapped/posts/${postId}/generate`, {
    method: "POST",
  });
}

export function approveUnwrappedStory(storyId: string): Promise<UnwrappedReviewStory> {
  return unwrappedRequest<UnwrappedReviewStory>(`/admin/unwrapped/${storyId}/approve`, {
    method: "POST",
  });
}

export function rejectUnwrappedStory(
  storyId: string,
  reason: string,
): Promise<UnwrappedReviewStory> {
  return unwrappedRequest<UnwrappedReviewStory>(`/admin/unwrapped/${storyId}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}
