import { getAccessToken } from "../../auth";
import type {
  UnwrappedAdminPost,
  UnwrappedGenerationTrigger,
  UnwrappedGenerationMonitor,
  UnwrappedReviewStory,
  UnwrappedBenchmarkPrompt,
  UnwrappedBenchmarkResponse,
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

export function getUnwrappedBenchmarkPrompt(postId: number): Promise<UnwrappedBenchmarkPrompt> {
  return unwrappedRequest<UnwrappedBenchmarkPrompt>(
    `/api/admin/unwrapped/posts/${postId}/benchmark/context`,
  );
}

export function generateUnwrappedBenchmark(
  postId: number,
  systemPrompts: string[],
): Promise<UnwrappedBenchmarkResponse> {
  return unwrappedRequest<UnwrappedBenchmarkResponse>(
    `/api/admin/unwrapped/posts/${postId}/benchmark`,
    {
      method: "POST",
      body: JSON.stringify({ systemPrompts }),
    },
  ).then(requireCompleteBenchmarkContent);
}

function requireCompleteBenchmarkContent(
  response: UnwrappedBenchmarkResponse,
): UnwrappedBenchmarkResponse {
  for (const variant of response.variants) {
    if (variant.status !== "SUCCEEDED") continue;

    for (const option of response.options) {
      const page = Array.isArray(variant.argumentPages)
        ? variant.argumentPages.find((candidate) => candidate?.optionId === option.id)
        : undefined;
      const hasParagraphContent = Array.isArray(page?.paragraphs)
        && page.paragraphs.length > 0
        && page.paragraphs.every((paragraph) => (
          typeof paragraph?.text === "string"
          && paragraph.text.trim().length > 0
          && Array.isArray(paragraph.sourceIds)
        ));
      if (
        !page
        || typeof page.headline !== "string"
        || page.headline.trim().length === 0
        || !hasParagraphContent
        || !Array.isArray(page.sources)
      ) {
        throw new UnwrappedAdminApiError(
          502,
          `The model returned incomplete article content for ${option.label}.`,
        );
      }
    }
  }
  return response;
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
