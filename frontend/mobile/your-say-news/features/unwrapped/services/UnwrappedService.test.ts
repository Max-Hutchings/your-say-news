import { getUnwrapped, submitFollowUp } from "./UnwrappedService";

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        POST_SERVICE_HOST: "http://posts.local:",
        POST_SERVICE_PORT: "8082",
      },
    },
  },
}));

const mockGet = jest.fn();
const mockPost = jest.fn();
jest.mock("@/features/auth", () => ({
  YsnHttpClient: {
    getSecure: () => ({
      get: (...args: unknown[]) => mockGet(...args),
      post: (...args: unknown[]) => mockPost(...args),
    }),
  },
}));

beforeEach(() => jest.clearAllMocks());

test("loads the authenticated post-vote story from its exact post endpoint", async () => {
  const response = {
    state: "READY",
    notice: "This analysis describes people who voted on this post.",
    originalOptionId: 71,
    existingFollowUpOptionId: null,
    story: { storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f", argumentPages: [] },
  };
  mockGet.mockResolvedValue({ data: response });

  await expect(getUnwrapped(7)).resolves.toEqual(response);
  expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/posts/7/unwrapped");
});

test("submits only the reconsidered option to the story the user actually viewed", async () => {
  const response = {
    id: "1298e071-1fba-4be7-90ec-2dbcfd0b33c1",
    postId: 7,
    storyId: "4e11bdba-3ae0-4c76-963a-d5b3b2db597f",
    originalOptionId: 71,
    optionId: 72,
    changed: true,
    createdAt: "2026-07-25T13:00:00Z",
  };
  mockPost.mockResolvedValue({ data: response });

  await expect(submitFollowUp(7, response.storyId, 72)).resolves.toEqual(response);
  expect(mockPost).toHaveBeenCalledWith(
    "http://posts.local:8082/posts/7/unwrapped/4e11bdba-3ae0-4c76-963a-d5b3b2db597f/follow-up",
    { optionId: 72 }
  );
});

test("propagates a gated or unavailable response instead of manufacturing a story", async () => {
  const forbidden = { isAxiosError: true, response: { status: 403 } };
  mockGet.mockRejectedValue(forbidden);
  await expect(getUnwrapped(88)).rejects.toBe(forbidden);

  const conflict = { isAxiosError: true, response: { status: 409 } };
  mockPost.mockRejectedValue(conflict);
  await expect(submitFollowUp(7, "4e11bdba-3ae0-4c76-963a-d5b3b2db597f", 72))
    .rejects.toBe(conflict);
});
