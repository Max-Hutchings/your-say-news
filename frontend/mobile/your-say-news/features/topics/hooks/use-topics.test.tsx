import { renderHook, waitFor } from "@testing-library/react-native";
import { listTopicTags } from "../services/TopicService";
import { resetTopicTagsCacheForTests, useTopicTags } from "./use-topics";

jest.mock("../services/TopicService", () => ({ listTopicTags: jest.fn() }));

const mockListTopicTags = listTopicTags as jest.Mock;
const catalogue = [
  { id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true },
];

beforeEach(() => {
  jest.clearAllMocks();
  resetTopicTagsCacheForTests();
});

it("shares one catalogue request across consumers and serves later mounts from cache", async () => {
  mockListTopicTags.mockResolvedValue(catalogue);
  const first = renderHook(() => useTopicTags());
  await waitFor(() => expect(first.result.current).toEqual({
    topicTags: catalogue, loading: false, error: null,
  }));

  const second = renderHook(() => useTopicTags());
  await waitFor(() => expect(second.result.current).toEqual({
    topicTags: catalogue, loading: false, error: null,
  }));
  expect(mockListTopicTags).toHaveBeenCalledTimes(1);
});

it("exposes a useful error and stops loading when the catalogue request fails", async () => {
  mockListTopicTags.mockRejectedValue(new Error("network unavailable"));
  const { result } = renderHook(() => useTopicTags());

  await waitFor(() => expect(result.current).toEqual({
    topicTags: [], loading: false, error: "Topic tags could not be loaded.",
  }));
});
