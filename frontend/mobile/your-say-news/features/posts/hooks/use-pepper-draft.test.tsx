import { act, renderHook, waitFor } from "@testing-library/react-native";
import {
  getActivePepperGeneration,
  getLatestPepperDraft,
  reconnectPepperGeneration,
  savePepperDraft,
  streamPepperGeneration,
} from "../services/PepperAgentService";
import { usePepperDraft } from "./use-pepper-draft";
import type { PepperDraftRecord, PepperPostDraft } from "../types";

jest.mock("../services/PepperAgentService", () => ({
  getActivePepperGeneration: jest.fn(),
  getLatestPepperDraft: jest.fn(),
  reconnectPepperGeneration: jest.fn(),
  savePepperDraft: jest.fn(),
  streamPepperGeneration: jest.fn(),
}));

const mockActive = getActivePepperGeneration as jest.Mock;
const mockLatest = getLatestPepperDraft as jest.Mock;
const mockReconnect = reconnectPepperGeneration as jest.Mock;
const mockSave = savePepperDraft as jest.Mock;
const mockStream = streamPepperGeneration as jest.Mock;

const content: PepperPostDraft = {
  summary: "A sourced overview.",
  supportQuestion: "Should the proposal proceed?",
  caseFor: "The strongest case for.",
  caseAgainst: "The strongest case against.",
  votingType: "BINARY",
  voteOptions: ["Agree", "Disagree"],
  citations: [{ url: "https://www.ons.gov.uk/work", title: "Working patterns", publisher: "ONS" }],
};

const restored: PepperDraftRecord = {
  id: "draft-41",
  prompt: "Research the proposal",
  status: "FINISHED",
  success: true,
  replicaId: "replica-a",
  content,
  errorMessage: null,
  publishedPostId: null,
  version: 1,
};

beforeEach(() => {
  jest.clearAllMocks();
  mockActive.mockResolvedValue(null);
  mockLatest.mockResolvedValue(null);
});

it("restores the newest unpublished server draft when Pepper opens", async () => {
  mockLatest.mockResolvedValue(restored);
  const { result } = renderHook(() => usePepperDraft());

  await waitFor(() => expect(result.current.draft).toEqual(restored));
  expect(result.current.status).toBe("FINISHED");
  expect(mockLatest).toHaveBeenCalledTimes(1);
});

it("reconnects an active generation with its draft and replica ids after refresh", async () => {
  mockActive.mockResolvedValue({ draftId: "draft-41", replicaId: "replica-a" });
  mockLatest.mockResolvedValue({ ...restored, status: "GENERATING", success: null, content: null });
  mockReconnect.mockImplementation(async (_draftId, _replicaId, onEvent) => {
    onEvent({ status: "GENERATING", draftId: "draft-41", replicaId: "replica-a" });
    onEvent({ status: "FINISHED", draftId: "draft-41", replicaId: "replica-a", result: content });
  });
  const { result } = renderHook(() => usePepperDraft());

  await waitFor(() => expect(result.current.status).toBe("FINISHED"));
  expect(mockReconnect).toHaveBeenCalledWith(
    "draft-41",
    "replica-a",
    expect.any(Function),
  );
  expect(result.current.draft?.content).toEqual(content);
});

it("debounces editable draft changes into versioned server autosaves", async () => {
  jest.useFakeTimers();
  mockLatest.mockResolvedValue(restored);
  mockSave.mockResolvedValue({ ...restored, version: 2, content: { ...content, summary: "Edited." } });
  const { result } = renderHook(() => usePepperDraft());
  await waitFor(() => expect(result.current.draft).toEqual(restored));

  act(() => result.current.changeDraft({ ...content, summary: "Edited." }));
  expect(result.current.draft?.content?.summary).toBe("Edited.");
  expect(mockSave).not.toHaveBeenCalled();
  await act(async () => {
    jest.advanceTimersByTime(600);
    await Promise.resolve();
  });

  expect(mockSave).toHaveBeenCalledWith(
    "draft-41",
    { ...content, summary: "Edited." },
    1,
  );
  await waitFor(() => expect(result.current.draft?.version).toBe(2));

  act(() => result.current.changeDraft({ ...content, summary: "Edited again." }));
  await act(async () => {
    jest.advanceTimersByTime(600);
    await Promise.resolve();
  });
  expect(mockSave).toHaveBeenLastCalledWith(
    "draft-41",
    { ...content, summary: "Edited again." },
    2,
  );
  jest.useRealTimers();
});

it("coalesces an edit made during an in-flight save and uses the returned version", async () => {
  jest.useFakeTimers();
  mockLatest.mockResolvedValue(restored);
  let resolveFirst!: (saved: PepperDraftRecord) => void;
  mockSave
    .mockReturnValueOnce(new Promise<PepperDraftRecord>((resolve) => { resolveFirst = resolve; }))
    .mockImplementationOnce(async (_id, nextContent) => ({
      ...restored,
      version: 3,
      content: nextContent,
    }));
  const { result } = renderHook(() => usePepperDraft());
  await waitFor(() => expect(result.current.draft).toEqual(restored));

  const firstEdit = { ...content, summary: "First edit." };
  act(() => result.current.changeDraft(firstEdit));
  await act(async () => {
    jest.advanceTimersByTime(600);
    await Promise.resolve();
  });
  expect(mockSave).toHaveBeenCalledWith("draft-41", firstEdit, 1);

  const newestEdit = { ...content, summary: "Newest edit." };
  act(() => result.current.changeDraft(newestEdit));
  await act(async () => {
    jest.advanceTimersByTime(600);
    await Promise.resolve();
  });
  expect(mockSave).toHaveBeenCalledTimes(1);

  await act(async () => {
    resolveFirst({ ...restored, version: 2, content: firstEdit });
    await Promise.resolve();
    await Promise.resolve();
  });
  expect(mockSave).toHaveBeenLastCalledWith("draft-41", newestEdit, 2);
  await waitFor(() => expect(result.current.draft).toEqual({
    ...restored,
    version: 3,
    content: newestEdit,
  }));
  jest.useRealTimers();
});

it("forwards a prompt and adopts each streamed generation state", async () => {
  mockStream.mockImplementation(async (prompt, onEvent) => {
    expect(prompt).toBe("Research a current housing proposal");
    onEvent({ status: "RECEIVED", draftId: "draft-52", replicaId: "replica-b" });
    onEvent({ status: "GENERATING", draftId: "draft-52", replicaId: "replica-b" });
    onEvent({ status: "FINISHED", draftId: "draft-52", replicaId: "replica-b", result: content });
  });
  mockLatest
    .mockResolvedValueOnce(null)
    .mockResolvedValueOnce({ ...restored, id: "draft-52", replicaId: "replica-b" });
  const { result } = renderHook(() => usePepperDraft());
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => result.current.generate("Research a current housing proposal"));

  expect(mockStream).toHaveBeenCalledWith(
    "Research a current housing proposal",
    expect.any(Function),
  );
  expect(result.current.status).toBe("FINISHED");
  expect(result.current.draft).toEqual({ ...restored, id: "draft-52", replicaId: "replica-b" });
});

it("maps provider and connection failures to the agreed safe message", async () => {
  mockStream.mockRejectedValue(new Error("provider internals"));
  const { result } = renderHook(() => usePepperDraft());
  await waitFor(() => expect(result.current.loading).toBe(false));

  await act(async () => result.current.generate("Research a current issue"));

  expect(result.current.status).toBe("FAILED");
  expect(result.current.error).toBe("Pepper AI is having trouble, please try again later.");
});
