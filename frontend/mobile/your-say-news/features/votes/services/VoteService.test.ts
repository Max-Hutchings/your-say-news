import { castVote, getMyVote } from "./VoteService";

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

const mockPost = jest.fn();
const mockGet = jest.fn();
jest.mock("@/features/auth", () => ({
  YsnHttpClient: {
    getSecure: () => ({
      post: (...args: unknown[]) => mockPost(...args),
      get: (...args: unknown[]) => mockGet(...args),
    }),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

describe("castVote", () => {
  it("posts the postId and stance to /votes and returns the created vote", async () => {
    mockPost.mockResolvedValue({ data: { id: 9, postId: 3, optionId: 31 } });

    const vote = await castVote(3, 31);

    expect(mockPost).toHaveBeenCalledWith("http://posts.local:8082/votes", {
      postId: 3,
      optionId: 31,
    });
    expect(vote).toEqual({ id: 9, postId: 3, optionId: 31 });
  });

  it("never sends a userId — identity is taken from the token server-side", async () => {
    mockPost.mockResolvedValue({ data: { id: 1, postId: 3, optionId: 32 } });

    await castVote(3, 32);

    const body = mockPost.mock.calls[0][1] as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(["optionId", "postId"]);
  });
});

describe("getMyVote", () => {
  it("returns the caller's vote when the service responds 200", async () => {
    mockGet.mockResolvedValue({ status: 200, data: { id: 9, postId: 3, optionId: 32 } });

    const vote = await getMyVote(3);

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/votes/3/mine");
    expect(vote).toEqual({ id: 9, postId: 3, optionId: 32 });
  });

  it("returns null when the service responds 204 (not voted yet)", async () => {
    mockGet.mockResolvedValue({ status: 204, data: null });

    expect(await getMyVote(3)).toBeNull();
  });
});
