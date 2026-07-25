import {
  followUser,
  getMyProfile,
  getProfile,
  listConnections,
  unfollowUser,
} from "./ProfileService";

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        USER_SERVICE_HOST: "http://users.local:",
        USER_SERVICE_PORT: "8082",
      },
    },
  },
}));

const mockGet = jest.fn();
const mockPost = jest.fn();
const mockDelete = jest.fn();
jest.mock("@/features/auth", () => ({
  YsnHttpClient: {
    getSecure: () => ({
      get: (...args: unknown[]) => mockGet(...args),
      post: (...args: unknown[]) => mockPost(...args),
      delete: (...args: unknown[]) => mockDelete(...args),
    }),
  },
}));

beforeEach(() => jest.clearAllMocks());

const profile = {
  id: 17,
  displayName: "Amina Khan",
  handle: "amina.k",
  avatarUrl: null,
  followerCount: 12,
  followingCount: 8,
  followedByViewer: false,
};

test("profile lookups distinguish a missing profile from a returned profile", async () => {
  mockGet
    .mockResolvedValueOnce({ status: 200, data: profile })
    .mockResolvedValueOnce({ status: 204 })
    .mockResolvedValueOnce({ status: 204 });

  await expect(getMyProfile()).resolves.toEqual(profile);
  await expect(getProfile(99)).resolves.toBeNull();
  await expect(getMyProfile()).resolves.toBeNull();
  expect(mockGet).toHaveBeenNthCalledWith(1, "http://users.local:8082/profiles/me");
  expect(mockGet).toHaveBeenNthCalledWith(2, "http://users.local:8082/profiles/99");
});

test("follow and unfollow return the updated relationship counters", async () => {
  const followed = { userId: 17, following: true, followerCount: 13, followingCount: 8 };
  const unfollowed = { ...followed, following: false, followerCount: 12 };
  mockPost.mockResolvedValue({ data: followed });
  mockDelete.mockResolvedValue({ data: unfollowed });

  await expect(followUser(17)).resolves.toEqual(followed);
  await expect(unfollowUser(17)).resolves.toEqual(unfollowed);
  expect(mockPost).toHaveBeenCalledWith("http://users.local:8082/social/follows/17");
  expect(mockDelete).toHaveBeenCalledWith("http://users.local:8082/social/follows/17");
});

test("connections passes through a custom page size and returns the requested page", async () => {
  const page = {
    items: [{ id: 21, displayName: "Sam Okafor", handle: "sam.o" }],
    hasMore: true,
  };
  mockGet.mockResolvedValue({ data: page });

  await expect(listConnections(17, "followers", 2, 12)).resolves.toEqual(page);
  expect(mockGet).toHaveBeenCalledWith(
    "http://users.local:8082/social/17/followers?page=2&size=12",
  );
});
