import Constants from "expo-constants";
import { fetch as expoFetch } from "expo/fetch";
import * as SecureStore from "expo-secure-store";
import { useAuthStore, YsnHttpClient } from "@/features/auth";
import type {
  PepperDraftRecord,
  PepperGenerationEvent,
  PepperPostDraft,
} from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const PEPPER_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/agent/drafts`;
const SAFE_FAILURE = "Pepper AI is having trouble, please try again later.";

export const ACTIVE_PEPPER_GENERATION_KEY = "active-pepper-generation";

type ActiveGeneration = { draftId: string; replicaId: string };

async function accessToken(): Promise<string> {
  const state = useAuthStore.getState();
  let token = state.accessToken;
  if (state.accessTokenExpired()) token = await state.refreshAccessToken();
  if (!token) throw new Error(SAFE_FAILURE);
  return token;
}

async function stream(
  url: string,
  init: { method: "POST" | "GET"; body?: string; replicaId?: string },
  onEvent: (event: PepperGenerationEvent) => void,
): Promise<void> {
  const token = await accessToken();
  const headers: Record<string, string> = {
    Accept: "text/event-stream",
    Authorization: `Bearer ${token}`,
  };
  if (init.body) headers["Content-Type"] = "application/json";
  if (init.replicaId) headers["X-Pepper-Replica"] = init.replicaId;

  let response: Awaited<ReturnType<typeof expoFetch>>;
  try {
    response = await expoFetch(url, {
      method: init.method,
      headers,
      ...(init.body ? { body: init.body } : {}),
      credentials: "include",
    });
  } catch {
    throw new Error(SAFE_FAILURE);
  }
  if (!response.ok || !response.body) throw new Error(SAFE_FAILURE);

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() ?? "";
      for (const block of blocks) {
        const payload = block
          .split(/\r?\n/)
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trimStart())
          .join("\n");
        if (!payload) continue;
        const event = JSON.parse(payload) as PepperGenerationEvent;
        if (event.status === "RECEIVED") {
          await SecureStore.setItemAsync(
            ACTIVE_PEPPER_GENERATION_KEY,
            JSON.stringify({ draftId: event.draftId, replicaId: event.replicaId }),
          );
        }
        onEvent(event);
        if (event.status === "FINISHED" || event.status === "FAILED") {
          await SecureStore.deleteItemAsync(ACTIVE_PEPPER_GENERATION_KEY);
        }
      }
    }
  } catch {
    throw new Error(SAFE_FAILURE);
  }
}

export async function streamPepperGeneration(
  prompt: string,
  onEvent: (event: PepperGenerationEvent) => void,
): Promise<void> {
  return stream(
    PEPPER_URL,
    { method: "POST", body: JSON.stringify({ request: prompt }) },
    onEvent,
  );
}

export async function reconnectPepperGeneration(
  draftId: string,
  replicaId: string,
  onEvent: (event: PepperGenerationEvent) => void,
): Promise<void> {
  return stream(
    `${PEPPER_URL}/${draftId}/events`,
    { method: "GET", replicaId },
    onEvent,
  );
}

export async function getLatestPepperDraft(): Promise<PepperDraftRecord | null> {
  const response = await YsnHttpClient.getSecure().get<PepperDraftRecord>(`${PEPPER_URL}/latest`);
  return response.status === 204 || !response.data ? null : response.data;
}

export async function savePepperDraft(
  draftId: string,
  content: PepperPostDraft,
  version: number,
): Promise<PepperDraftRecord> {
  const { data } = await YsnHttpClient.getSecure().put<PepperDraftRecord>(
    `${PEPPER_URL}/${draftId}`,
    { content, version },
  );
  return data;
}

export async function getActivePepperGeneration(): Promise<ActiveGeneration | null> {
  const stored = await SecureStore.getItemAsync(ACTIVE_PEPPER_GENERATION_KEY);
  if (!stored) return null;
  try {
    const value = JSON.parse(stored) as Partial<ActiveGeneration>;
    if (typeof value.draftId === "string" && typeof value.replicaId === "string") {
      return { draftId: value.draftId, replicaId: value.replicaId };
    }
  } catch {
    // Invalid local affinity data cannot safely route a reconnect.
  }
  await SecureStore.deleteItemAsync(ACTIVE_PEPPER_GENERATION_KEY);
  return null;
}
