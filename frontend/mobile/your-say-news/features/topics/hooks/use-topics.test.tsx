import { renderHook, waitFor } from "@testing-library/react-native";
import { listTopics } from "../services/TopicService";
import { resetTopicsCacheForTests, useTopics } from "./use-topics";

jest.mock("../services/TopicService", () => ({ listTopics: jest.fn() }));

const mockListTopics = listTopics as jest.Mock;
const catalogue = [
  { id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true },
];

beforeEach(() => {
  jest.clearAllMocks();
  resetTopicsCacheForTests();
});

it("shares one catalogue request across consumers and serves later mounts from cache", async () => {
  mockListTopics.mockResolvedValue(catalogue);
  const first = renderHook(() => useTopics());
  await waitFor(() => expect(first.result.current).toEqual({ topics: catalogue, loading: false, error: null }));

  const second = renderHook(() => useTopics());
  await waitFor(() => expect(second.result.current).toEqual({ topics: catalogue, loading: false, error: null }));
  expect(mockListTopics).toHaveBeenCalledTimes(1);
});

it("exposes a useful error and stops loading when the catalogue request fails", async () => {
  mockListTopics.mockRejectedValue(new Error("network unavailable"));
  const { result } = renderHook(() => useTopics());

  await waitFor(() => expect(result.current).toEqual({
    topics: [], loading: false, error: "Topics could not be loaded.",
  }));
});
