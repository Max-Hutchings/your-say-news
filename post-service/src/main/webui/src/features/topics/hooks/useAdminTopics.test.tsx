import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createAdminTopic, getAdminTopics, setAdminTopicActive } from "../services/topicAdminApi";
import { useAdminTopics } from "./useAdminTopics";

vi.mock("../services/topicAdminApi", async (importOriginal) => {
  const original = await importOriginal<typeof import("../services/topicAdminApi")>();
  return { ...original, createAdminTopic: vi.fn(), getAdminTopics: vi.fn(), setAdminTopicActive: vi.fn() };
});

const housing = { id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true };
const politics = { id: "politics", label: "Politics", displayGroup: "Politics & government", displayOrder: 1, active: true };

describe("useAdminTopics", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getAdminTopics).mockResolvedValue([housing]);
  });

  it("inserts a created topic in catalogue order and replaces a retired row", async () => {
    vi.mocked(createAdminTopic).mockResolvedValue(politics);
    vi.mocked(setAdminTopicActive).mockResolvedValue({ ...housing, active: false });
    const { result } = renderHook(() => useAdminTopics());
    await waitFor(() => expect(result.current.topics).toEqual([housing]));

    await act(async () => { await result.current.add({ label: "Politics", displayGroup: "Politics & government" }); });
    expect(result.current.topics).toEqual([politics, housing]);

    await act(async () => { await result.current.setActive("housing", false); });
    expect(result.current.topics).toEqual([politics, { ...housing, active: false }]);
    expect(result.current.savingIds).toEqual(new Set());
  });

  it("keeps catalogue state and exposes an error when an update fails", async () => {
    vi.mocked(setAdminTopicActive).mockRejectedValue(new Error("network unavailable"));
    const { result } = renderHook(() => useAdminTopics());
    await waitFor(() => expect(result.current.topics).toEqual([housing]));

    await act(async () => { await expect(result.current.setActive("housing", false)).rejects.toThrow("network unavailable"); });
    expect(result.current.topics).toEqual([housing]);
    expect(result.current.error).toEqual({ status: null, message: "network unavailable" });
  });
});
