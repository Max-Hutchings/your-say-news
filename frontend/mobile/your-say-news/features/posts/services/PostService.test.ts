import { getFeed, getRecent, FEED_PAGE_SIZE } from "./PostService";

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
  YsnHttpClient: { getSecure: () => ({ get: (...args: unknown[]) => mockGet(...args) }) },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

describe("getRecent", () => {
  it("requests the first page of FEED_PAGE_SIZE posts by default", async () => {
    mockGet.mockResolvedValue({ data: [{ id: 1 }] });

    const posts = await getRecent();

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/posts", {
      params: { page: 0, size: FEED_PAGE_SIZE },
    });
    expect(posts).toEqual([{ id: 1 }]);
  });

  it("passes the requested page and size through as query params", async () => {
    mockGet.mockResolvedValue({ data: [] });

    await getRecent(2, FEED_PAGE_SIZE);

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/posts", {
      params: { page: 2, size: FEED_PAGE_SIZE },
    });
  });

  it("returns an empty array when the service sends no body", async () => {
    mockGet.mockResolvedValue({ data: null });

    expect(await getRecent(9)).toEqual([]);
  });
});

describe("getFeed", () => {
  it("sends the selected post type with every ranked feed page request", async () => {
    mockGet.mockResolvedValue({ data: [{ id: 8 }] });

    const posts = await getFeed(2, FEED_PAGE_SIZE, "VIDEO");

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/feed", {
      params: { page: 2, size: FEED_PAGE_SIZE, type: "VIDEO" },
    });
    expect(posts).toEqual([{ id: 8 }]);
  });

  it("omits the type query parameter for the unfiltered feed", async () => {
    mockGet.mockResolvedValue({ data: [] });

    await getFeed();

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/feed", {
      params: { page: 0, size: FEED_PAGE_SIZE },
    });
  });
});
