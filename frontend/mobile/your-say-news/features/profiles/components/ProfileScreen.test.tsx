import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { RefreshControl } from "react-native";
import { ThemeProvider } from "@/constants/theme";
import { ProfileScreen } from "./ProfileScreen";

const mockBack = jest.fn();
const mockPush = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack, push: mockPush }),
}));
jest.mock("@expo/vector-icons", () => ({ Ionicons: () => null }));

const mockGetMyProfile = jest.fn();
const mockGetProfile = jest.fn();
const mockFollowUser = jest.fn();
const mockUnfollowUser = jest.fn();
jest.mock("../services/ProfileService", () => ({
  getMyProfile: (...args: unknown[]) => mockGetMyProfile(...args),
  getProfile: (...args: unknown[]) => mockGetProfile(...args),
  followUser: (...args: unknown[]) => mockFollowUser(...args),
  unfollowUser: (...args: unknown[]) => mockUnfollowUser(...args),
}));

const mockListByUser = jest.fn();
jest.mock("@/features/posts", () => ({
  listByUser: (...args: unknown[]) => mockListByUser(...args),
  PostCard: ({ post }: { post: { title: string } }) => {
    const { Text: MockText } = require("react-native");
    return <MockText>{post.title}</MockText>;
  },
}));

const profile = {
  id: 17,
  displayName: "Amina Khan",
  handle: "amina.k",
  avatarUrl: null,
  followerCount: 12,
  followingCount: 8,
  followedByViewer: false,
};
const posts = [
  { id: 3, title: "Four-day weeks and productivity" },
  { id: 2, title: "Local climate adaptation" },
];

beforeEach(() => {
  jest.clearAllMocks();
  mockGetMyProfile.mockResolvedValue(profile);
  mockGetProfile.mockResolvedValue(profile);
  mockListByUser.mockResolvedValue(posts);
});

function renderProfile(userId?: number) {
  return render(
    <ThemeProvider>
      <ProfileScreen userId={userId} />
    </ThemeProvider>,
  );
}

test("loads the signed-in reader's profile and posts", async () => {
  renderProfile();

  expect(await screen.findByText("Amina Khan")).toBeTruthy();
  expect(screen.getByText("@amina.k")).toBeTruthy();
  expect(screen.getByText("Four-day weeks and productivity")).toBeTruthy();
  expect(screen.getByText("Local climate adaptation")).toBeTruthy();
  expect(mockGetMyProfile).toHaveBeenCalledTimes(1);
  expect(mockGetProfile).not.toHaveBeenCalled();
  expect(mockListByUser).toHaveBeenCalledWith(17);
  expect(screen.queryByText("Follow")).toBeNull();
});

test("loads another profile and follows it with server-returned counters", async () => {
  mockFollowUser.mockResolvedValue({
    userId: 17,
    following: true,
    followerCount: 13,
    followingCount: 8,
  });
  renderProfile(17);
  await screen.findByText("Amina Khan");

  fireEvent.press(screen.getByText("Follow"));

  expect(await screen.findByText("Following")).toBeTruthy();
  expect(mockGetProfile).toHaveBeenCalledWith(17);
  expect(mockFollowUser).toHaveBeenCalledWith(17);
  expect(screen.getByLabelText("13 Followers")).toBeTruthy();
});

test("unfollows a profile that the reader already follows", async () => {
  mockGetProfile.mockResolvedValue({ ...profile, followedByViewer: true });
  mockUnfollowUser.mockResolvedValue({
    userId: 17,
    following: false,
    followerCount: 11,
    followingCount: 8,
  });
  renderProfile(17);

  await screen.findByText("Amina Khan");
  const followingLabels = screen.getAllByText("Following");
  const followingButtonLabel = followingLabels[followingLabels.length - 1];
  fireEvent.press(followingButtonLabel);

  expect(await screen.findByText("Follow")).toBeTruthy();
  expect(mockUnfollowUser).toHaveBeenCalledWith(17);
  expect(screen.getByLabelText("11 Followers")).toBeTruthy();
});

test("opens follower and following lists and supports back navigation", async () => {
  renderProfile(17);
  await screen.findByText("Amina Khan");

  fireEvent.press(screen.getByLabelText("12 Followers"));
  fireEvent.press(screen.getByLabelText("8 Following"));
  fireEvent.press(screen.getByLabelText("Back"));

  expect(mockPush).toHaveBeenNthCalledWith(
    1,
    "/profiles/17/connections?tab=followers",
  );
  expect(mockPush).toHaveBeenNthCalledWith(
    2,
    "/profiles/17/connections?tab=following",
  );
  expect(mockBack).toHaveBeenCalledTimes(1);
});

test("renders not-found and request-failure states without requesting posts", async () => {
  mockGetProfile.mockResolvedValueOnce(null);
  const first = renderProfile(404);
  expect(await screen.findByText("Profile not found.")).toBeTruthy();
  expect(mockListByUser).not.toHaveBeenCalled();
  first.unmount();

  mockGetProfile.mockRejectedValueOnce(new Error("offline"));
  renderProfile(17);
  expect(await screen.findByText("Profile unavailable.")).toBeTruthy();
});

test("reports a post-loading failure instead of showing a misleading empty profile", async () => {
  mockListByUser.mockRejectedValueOnce(new Error("posts unavailable"));

  renderProfile(17);

  expect(await screen.findByText("Profile unavailable.")).toBeTruthy();
  expect(screen.queryByText("Amina Khan")).toBeNull();
  expect(screen.queryByText("No posts yet.")).toBeNull();
});

test("pull-to-refresh replaces stale profile counters and posts", async () => {
  mockGetMyProfile
    .mockResolvedValueOnce(profile)
    .mockResolvedValueOnce({ ...profile, displayName: "Amina K.", followerCount: 14 });
  mockListByUser
    .mockResolvedValueOnce(posts)
    .mockResolvedValueOnce([{ id: 4, title: "Updated evidence review" }]);
  const view = renderProfile();
  await screen.findByText("Amina Khan");
  const refreshControl = view.UNSAFE_getByType(RefreshControl);

  fireEvent(refreshControl, "refresh");

  expect(await screen.findByText("Amina K.")).toBeTruthy();
  expect(screen.getByText("Updated evidence review")).toBeTruthy();
  expect(screen.getByLabelText("14 Followers")).toBeTruthy();
  expect(screen.queryByText("Four-day weeks and productivity")).toBeNull();
  await waitFor(() => expect(mockGetMyProfile).toHaveBeenCalledTimes(2));
  expect(mockListByUser).toHaveBeenCalledTimes(2);
});
