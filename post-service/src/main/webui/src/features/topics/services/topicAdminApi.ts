import { adminFetch } from "../../auth";
import type { AdminTopic, CreateTopicInput } from "../types";

export class TopicAdminApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

async function topicRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await adminFetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const body = await response.json().catch(() => null) as { message?: string } | null;
    throw new TopicAdminApiError(response.status, body?.message ?? "The topic tag request failed.");
  }
  return response.json() as Promise<T>;
}

export function getAdminTopics(): Promise<AdminTopic[]> {
  return topicRequest<AdminTopic[]>("/api/admin/topic-tags");
}

export function createAdminTopic(input: CreateTopicInput): Promise<AdminTopic> {
  return topicRequest<AdminTopic>("/api/admin/topic-tags", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function setAdminTopicActive(topicId: string, active: boolean): Promise<AdminTopic> {
  return topicRequest<AdminTopic>(`/api/admin/topic-tags/${topicId}/active`, {
    method: "PUT",
    body: JSON.stringify({ active }),
  });
}
