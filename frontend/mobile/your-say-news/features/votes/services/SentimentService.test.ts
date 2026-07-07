import { getOverallSentiment, getAxisSentiment } from "./SentimentService";

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
jest.mock("@/features/auth", () => ({
  YsnHttpClient: {
    getSecure: () => ({
      get: (...args: unknown[]) => mockGet(...args),
    }),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

describe("getOverallSentiment", () => {
  it("gets /votes/{postId}/sentiment and returns the overall breakdown", async () => {
    const body = {
      postId: 3,
      characteristic: "OVERALL",
      buckets: [{ bucket: "OVERALL", yesCount: 40, noCount: 10, total: 50, yesPct: 80.0, noPct: 20.0 }],
      suppressedBuckets: 0,
    };
    mockGet.mockResolvedValue({ data: body });

    const result = await getOverallSentiment(3);

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/votes/3/sentiment");
    expect(result).toEqual(body);
  });
});

describe("getAxisSentiment", () => {
  it("gets /votes/{postId}/sentiment/{axis} and returns the axis breakdown", async () => {
    const body = {
      postId: 3,
      characteristic: "politicalPersuasion",
      buckets: [
        { bucket: "LEFT", yesCount: 40, noCount: 10, total: 50, yesPct: 80.0, noPct: 20.0 },
        { bucket: "RIGHT", yesCount: 5, noCount: 15, total: 20, yesPct: 25.0, noPct: 75.0 },
      ],
      suppressedBuckets: 0,
    };
    mockGet.mockResolvedValue({ data: body });

    const result = await getAxisSentiment(3, "politicalPersuasion");

    expect(mockGet).toHaveBeenCalledWith(
      "http://posts.local:8082/votes/3/sentiment/politicalPersuasion"
    );
    expect(result.buckets[0].bucket).toBe("LEFT");
    expect(result.buckets[0].yesPct).toBe(80.0);
  });

  it("propagates a 403 (not voted) so the hook can gate results behind voting", async () => {
    mockGet.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });

    await expect(getAxisSentiment(3, "gender")).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
