import {
  createPost,
  getFeed,
  getPost,
  getRecent,
  listByUser,
  FEED_PAGE_SIZE,
} from "./PostService";

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

    await getRecent(2, 11);

    expect(mockGet).toHaveBeenCalledWith("http://posts.local:8082/posts", {
      params: { page: 2, size: 11 },
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

describe("post creation and lookup", () => {
  it("creates a post without adding a client-controlled author id", async () => {
    const input = {
      title: "Four-day weeks and productivity",
      summary: "A review of the latest workplace trials.",
      type: "TEXT" as const,
    };
    const created = { id: 44, ...input, authorId: 7 };
    mockPost.mockResolvedValue({ data: created });

    await expect(createPost(input as never)).resolves.toEqual(created);
    expect(mockPost).toHaveBeenCalledWith("http://posts.local:8082/posts", input);
    expect(mockPost.mock.calls[0][1]).not.toHaveProperty("authorId");
  });

  it("returns a specific post and maps absent responses to null", async () => {
    const post = { id: 44, title: "Four-day weeks and productivity" };
    mockGet
      .mockResolvedValueOnce({ status: 200, data: post })
      .mockResolvedValueOnce({ status: 204 })
      .mockResolvedValueOnce({ status: 200, data: null });

    await expect(getPost(44)).resolves.toEqual(post);
    await expect(getPost(404)).resolves.toBeNull();
    await expect(getPost(405)).resolves.toBeNull();
    expect(mockGet).toHaveBeenNthCalledWith(1, "http://posts.local:8082/posts/44");
  });

  it("lists an author's posts and normalizes an empty body", async () => {
    const posts = [{ id: 4 }, { id: 3 }];
    mockGet
      .mockResolvedValueOnce({ data: posts })
      .mockResolvedValueOnce({ data: undefined });

    await expect(listByUser(17)).resolves.toEqual(posts);
    await expect(listByUser(18)).resolves.toEqual([]);
    expect(mockGet).toHaveBeenNthCalledWith(
      1,
      "http://posts.local:8082/posts/user/17",
    );
  });
});
