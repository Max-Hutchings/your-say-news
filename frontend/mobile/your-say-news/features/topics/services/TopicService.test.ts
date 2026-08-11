import { listTopicTags } from "./TopicService";

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: { expoConfig: { extra: { POST_SERVICE_HOST: "http://posts.local:", POST_SERVICE_PORT: "8082" } } },
}));

const mockGet = jest.fn();
jest.mock("@/features/auth", () => ({
  YsnHttpClient: { getSecure: () => ({ get: (...args: unknown[]) => mockGet(...args) }) },
}));

it("loads the active catalogue from the topic tags endpoint", async () => {
  const topics = [{ id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true }];
  mockGet.mockResolvedValue({ data: topics });

  await expect(listTopicTags()).resolves.toEqual(topics);
  expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/topic-tags");
});
