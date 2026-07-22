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
      votingType: "BINARY",
      characteristic: "OVERALL",
      options: [
        { id: 31, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
        { id: 32, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
      ],
      buckets: [{ bucket: "OVERALL", total: 50, choices: [
        { optionId: 31, count: 40, percentage: 80.0 },
        { optionId: 32, count: 10, percentage: 20.0 },
      ] }],
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
      votingType: "MULTIPLE_CHOICE",
      characteristic: "politicalPersuasion",
      options: [
        { id: 33, label: "More frequent buses", ordinal: 0, semanticKey: null },
        { id: 34, label: "Protected cycle lanes", ordinal: 1, semanticKey: null },
      ],
      buckets: [
        { bucket: "LEFT", total: 50, choices: [{ optionId: 33, count: 40, percentage: 80.0 }, { optionId: 34, count: 10, percentage: 20.0 }] },
        { bucket: "RIGHT", total: 20, choices: [{ optionId: 33, count: 5, percentage: 25.0 }, { optionId: 34, count: 15, percentage: 75.0 }] },
      ],
      suppressedBuckets: 0,
    };
    mockGet.mockResolvedValue({ data: body });

    const result = await getAxisSentiment(3, "politicalPersuasion");

    expect(mockGet).toHaveBeenCalledWith(
      "http://posts.local:8082/votes/3/sentiment/politicalPersuasion"
    );
    expect(result.buckets[0].bucket).toBe("LEFT");
    expect(result.buckets[0].choices[0].percentage).toBe(80.0);
  });

  it("propagates a 403 (not voted) so the hook can gate results behind voting", async () => {
    mockGet.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });

    await expect(getAxisSentiment(3, "gender")).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});
