import { getAccessToken } from "../../auth";
import type { AdminTopic, CreateTopicInput } from "../types";

export class TopicAdminApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

async function topicRequest<T>(path: string, init?: RequestInit): Promise<T> {
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
    throw new TopicAdminApiError(response.status, body?.message ?? "The topic request failed.");
  }
  return response.json() as Promise<T>;
}

export function getAdminTopics(): Promise<AdminTopic[]> {
  return topicRequest<AdminTopic[]>("/api/admin/topics");
}

export function createAdminTopic(input: CreateTopicInput): Promise<AdminTopic> {
  return topicRequest<AdminTopic>("/api/admin/topics", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function setAdminTopicActive(topicId: string, active: boolean): Promise<AdminTopic> {
  return topicRequest<AdminTopic>(`/api/admin/topics/${topicId}/active`, {
    method: "PUT",
    body: JSON.stringify({ active }),
  });
}
