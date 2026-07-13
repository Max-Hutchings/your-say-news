import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { ConnectionsScreen } from "./ConnectionsScreen";
import type { FollowPage, FollowUser } from "../types";

const mockPush = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ push: mockPush, back: jest.fn() }),
}));

const mockListConnections = jest.fn();
const mockFollowUser = jest.fn();
const mockUnfollowUser = jest.fn();
jest.mock("../services/ProfileService", () => ({
  CONNECTIONS_PAGE_SIZE: 50,
  listConnections: (...args: unknown[]) => mockListConnections(...args),
  followUser: (...args: unknown[]) => mockFollowUser(...args),
  unfollowUser: (...args: unknown[]) => mockUnfollowUser(...args),
}));

const user = (id: number, followed = false): FollowUser => ({
  id,
  displayName: `User ${id}`,
  handle: `user.${id}`,
  avatarUrl: null,
  followedByViewer: followed,
});

const page = (items: FollowUser[], hasMore: boolean): FollowPage => ({ items, hasMore });

function renderScreen(initialTab: "followers" | "following" = "followers") {
  return render(
    <ThemeProvider>
      <ConnectionsScreen userId={7} initialTab={initialTab} />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  jest.clearAllMocks();
});

test("loads the initial followers page for the viewed user", async () => {
  mockListConnections.mockResolvedValueOnce(page([user(1), user(2)], false));

  renderScreen("followers");

  expect(await screen.findByText("User 1")).toBeTruthy();
  expect(screen.getByText("User 2")).toBeTruthy();
  // Requested the first page of the followers list for user 7.
  expect(mockListConnections).toHaveBeenCalledWith(7, "followers", 0);
});

test("switching to the Following tab refetches that list", async () => {
  mockListConnections
    .mockResolvedValueOnce(page([user(1)], false)) // followers
    .mockResolvedValueOnce(page([user(9)], false)); // following

  renderScreen("followers");
  await screen.findByText("User 1");

  fireEvent.press(screen.getByText("Following"));

  expect(await screen.findByText("User 9")).toBeTruthy();
  expect(mockListConnections).toHaveBeenLastCalledWith(7, "following", 0);
});

test("scrolling to the end loads and appends the next page", async () => {
  mockListConnections
    .mockResolvedValueOnce(page([user(1)], true)) // page 0, more to come
    .mockResolvedValueOnce(page([user(2)], false)); // page 1

  renderScreen("followers");
  await screen.findByText("User 1");

  fireEvent(screen.getByTestId("connections-list"), "onEndReached");

  expect(await screen.findByText("User 2")).toBeTruthy();
  expect(screen.getByText("User 1")).toBeTruthy(); // previous page kept
  expect(mockListConnections).toHaveBeenLastCalledWith(7, "followers", 1);
});

test("does not page past the end when hasMore is false", async () => {
  mockListConnections.mockResolvedValueOnce(page([user(1)], false));

  renderScreen("followers");
  await screen.findByText("User 1");

  fireEvent(screen.getByTestId("connections-list"), "onEndReached");

  await waitFor(() => expect(mockListConnections).toHaveBeenCalledTimes(1));
});

test("tapping Follow on a row follows that user", async () => {
  mockListConnections.mockResolvedValueOnce(page([user(3, false)], false));
  mockFollowUser.mockResolvedValueOnce({ userId: 3, following: true, followerCount: 1, followingCount: 0 });

  renderScreen("followers");
  await screen.findByText("User 3");

  fireEvent.press(screen.getByLabelText("Follow user.3"));

  await waitFor(() => expect(mockFollowUser).toHaveBeenCalledWith(3));
  expect(await screen.findByLabelText("Unfollow user.3")).toBeTruthy();
});
