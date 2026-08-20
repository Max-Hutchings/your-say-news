import { fetch as expoFetch } from "expo/fetch";
import * as SecureStore from "expo-secure-store";
import { useAuthStore, YsnHttpClient } from "@/features/auth";
import {
  ACTIVE_PEPPER_GENERATION_KEY,
  getLatestPepperDraft,
  reconnectPepperGeneration,
  savePepperDraft,
  streamPepperGeneration,
} from "./PepperAgentService";
import type { PepperPostDraft } from "../types";

jest.mock("expo/fetch", () => ({ fetch: jest.fn() }));
jest.mock("expo-secure-store", () => ({
  setItemAsync: jest.fn(),
  getItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));
jest.mock("@/features/auth", () => ({
  useAuthStore: { getState: jest.fn() },
  YsnHttpClient: { getSecure: jest.fn() },
}));
jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: { extra: { POST_SERVICE_HOST: "http://posts.local:", POST_SERVICE_PORT: "8082" } },
  },
}));

const mockFetch = expoFetch as jest.Mock;
const mockGetState = useAuthStore.getState as jest.Mock;
const mockGetSecure = YsnHttpClient.getSecure as jest.Mock;

const generatedDraft: PepperPostDraft = {
  summary: "Trials found productivity was usually maintained, with results varying by sector.",
  supportQuestion: "Should more employers trial a four-day working week?",
  caseFor: "Shorter weeks may improve retention without reducing output.",
  caseAgainst: "Coverage costs can rise in services that need fixed opening hours.",
  votingType: "BINARY",
  voteOptions: ["Agree", "Disagree"],
  citations: [
    { url: "https://www.ons.gov.uk/work", title: "Working patterns", publisher: "ONS" },
  ],
};

function streamResponse(events: unknown[]) {
  const chunks = events.map((event) =>
    new TextEncoder().encode(`data:${JSON.stringify(event)}\n\n`),
  );
  let index = 0;
  return {
    ok: true,
    status: 200,
    body: {
      getReader: () => ({
        read: jest.fn(async () =>
          index < chunks.length ? { done: false, value: chunks[index++] } : { done: true },
        ),
      }),
    },
  };
}

function chunkedStreamResponse(chunks: string[]) {
  const values = chunks.map((chunk) => new TextEncoder().encode(chunk));
  let index = 0;
  return {
    ok: true,
    status: 200,
    body: {
      getReader: () => ({
        read: jest.fn(async () =>
          index < values.length ? { done: false, value: values[index++] } : { done: true },
        ),
      }),
    },
  };
}

beforeEach(() => {
  jest.clearAllMocks();
  mockGetState.mockReturnValue({
    accessToken: "pepper-token",
    accessTokenExpired: () => false,
    refreshAccessToken: jest.fn(),
  });
});

it("holds one authenticated SSE request through RECEIVED, GENERATING and FINISHED", async () => {
  mockFetch.mockResolvedValue(streamResponse([
    { status: "RECEIVED", draftId: "draft-41", replicaId: "replica-a" },
    { status: "GENERATING", draftId: "draft-41", replicaId: "replica-a" },
    { status: "FINISHED", draftId: "draft-41", replicaId: "replica-a", result: generatedDraft },
  ]));
  const onEvent = jest.fn();

  await streamPepperGeneration("Compare four-day week evidence", onEvent);

  expect(mockFetch).toHaveBeenCalledWith("http://posts.local:8082/agent/drafts", {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      Authorization: "Bearer pepper-token",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ request: "Compare four-day week evidence" }),
    credentials: "include",
  });
  expect(onEvent.mock.calls.map(([event]) => event.status)).toEqual([
    "RECEIVED",
    "GENERATING",
    "FINISHED",
  ]);
  expect(onEvent.mock.calls[2][0].result).toEqual(generatedDraft);
  expect(SecureStore.setItemAsync).toHaveBeenCalledWith(
    ACTIVE_PEPPER_GENERATION_KEY,
    JSON.stringify({ draftId: "draft-41", replicaId: "replica-a" }),
  );
  expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith(ACTIVE_PEPPER_GENERATION_KEY);
});

it("reconnects with both the persisted generation id and replica affinity header", async () => {
  mockFetch.mockResolvedValue(streamResponse([
    { status: "FINISHED", draftId: "draft-41", replicaId: "replica-a", result: generatedDraft },
  ]));

  await reconnectPepperGeneration("draft-41", "replica-a", jest.fn());

  expect(mockFetch).toHaveBeenCalledWith(
    "http://posts.local:8082/agent/drafts/draft-41/events",
    expect.objectContaining({
      method: "GET",
      headers: {
        Accept: "text/event-stream",
        Authorization: "Bearer pepper-token",
        "X-Pepper-Replica": "replica-a",
      },
      credentials: "include",
    }),
  );
});

it("parses SSE events split across reads and multiple events sharing one read", async () => {
  mockFetch.mockResolvedValue(chunkedStreamResponse([
    "data:{\"status\":\"RECE",
    "IVED\",\"draftId\":\"draft-41\",\"replicaId\":\"replica-a\"}\n\n"
      + "data:{\"status\":\"GENERATING\",\"draftId\":\"draft-41\",\"replicaId\":\"replica-a\"}\n\n"
      + "data:{\"status\":\"FIN",
    `ISHED\",\"draftId\":\"draft-41\",\"replicaId\":\"replica-a\",\"result\":${JSON.stringify(generatedDraft)}}\n\n`,
  ]));
  const onEvent = jest.fn();

  await streamPepperGeneration("Compare four-day week evidence", onEvent);

  expect(onEvent.mock.calls.map(([event]) => event.status)).toEqual([
    "RECEIVED",
    "GENERATING",
    "FINISHED",
  ]);
  expect(onEvent.mock.calls[2][0].result).toEqual(generatedDraft);
});

it("loads and saves the user's latest unpublished draft with an exact version", async () => {
  const get = jest.fn().mockResolvedValue({ data: { id: "draft-41", version: 3, content: generatedDraft } });
  const put = jest.fn().mockResolvedValue({ data: { id: "draft-41", version: 4, content: generatedDraft } });
  mockGetSecure.mockReturnValue({ get, put });

  await expect(getLatestPepperDraft()).resolves.toEqual({
    id: "draft-41",
    version: 3,
    content: generatedDraft,
  });
  await expect(savePepperDraft("draft-41", generatedDraft, 3)).resolves.toEqual({
    id: "draft-41",
    version: 4,
    content: generatedDraft,
  });
  expect(get).toHaveBeenCalledWith("http://posts.local:8082/agent/drafts/latest");
  expect(put).toHaveBeenCalledWith("http://posts.local:8082/agent/drafts/draft-41", {
    content: generatedDraft,
    version: 3,
  });
});

it("uses the safe Pepper failure message for a broken stream", async () => {
  mockFetch.mockResolvedValue({ ok: false, status: 503, body: null });

  await expect(streamPepperGeneration("Current policy", jest.fn())).rejects.toThrow(
    "Pepper AI is having trouble, please try again later.",
  );
});
