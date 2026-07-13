import React from "react";
import { FlatList } from "react-native";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { HomeFeed } from "./HomeFeed";
import { getFeed } from "../services/PostService";
import type { Post } from "../types";

jest.mock("expo-router", () => {
  const React = jest.requireActual("react");
  return {
    useRouter: () => ({ push: jest.fn() }),
    useFocusEffect: (callback: () => void | (() => void)) => React.useEffect(callback, [callback]),
  };
});

jest.mock("@/features/auth", () => ({
  useAuthStore: (selector: (state: { email: string }) => unknown) =>
    selector({ email: "reader@example.com" }),
}));

jest.mock("../services/PostService");

jest.mock("./PostCard", () => {
  const React = jest.requireActual("react");
  const { Pressable, Text } = jest.requireActual("react-native");
  return {
    PostCard: ({ post, onNextPost }: { post: Post; onNextPost?: () => void }) =>
      React.createElement(
        Pressable,
        { testID: `mock-post-${post.id}`, onPress: onNextPost },
        React.createElement(Text, null, post.summary)
      ),
  };
});

jest.mock("./Masthead", () => ({ Masthead: () => null }));
jest.mock("./FeedTabs", () => ({ FeedTabs: () => null }));

const mockGetFeed = getFeed as jest.Mock;

const posts: Post[] = [1, 2].map((id) => ({
  id,
  userId: 10 + id,
  summary: `Summary ${id}`,
  supportQuestion: `Support story ${id}?`,
  caseFor: null,
  caseAgainst: null,
  isUnbiased: false,
  createdAt: "2026-07-13T12:00:00Z",
  media: [],
}));

const videoPost: Post = {
  ...posts[1],
  id: 3,
  summary: "Video summary",
  media: [
    {
      mediaType: "VIDEO",
      orientation: "PORTRAIT",
      s3Key: "posts/video.mp4",
      contentType: "video/mp4",
      posterS3Key: "posts/video-poster.jpg",
      url: "https://media.local/video.mp4",
      posterUrl: "https://media.local/video-poster.jpg",
    },
  ],
};

describe("HomeFeed", () => {
  beforeEach(() => {
    mockGetFeed.mockReset();
  });

  it("scrolls the paged feed to the following post when the active card requests it", async () => {
    mockGetFeed.mockResolvedValue(posts);
    const scrollToIndex = jest
      .spyOn(FlatList.prototype, "scrollToIndex")
      .mockImplementation(() => undefined);

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );

    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });
    fireEvent.press(await screen.findByTestId("mock-post-1"));

    await waitFor(() =>
      expect(scrollToIndex).toHaveBeenCalledWith({ index: 1, animated: true })
    );
    scrollToIndex.mockRestore();
  });

  it("loads video posts by default and reloads from page one when the type changes", async () => {
    mockGetFeed.mockImplementation(
      (_page: number, _size: number, type?: "VIDEO" | "ARTICLE") => {
        if (type === "VIDEO") return Promise.resolve([videoPost]);
        if (type === "ARTICLE") return Promise.resolve([posts[0]]);
        return Promise.resolve([posts[0], videoPost]);
      }
    );

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });

    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(0, 5, "VIDEO")
    );
    expect(screen.getByLabelText("Video posts").props.accessibilityState.selected).toBe(true);
    expect(await screen.findByTestId("mock-post-3")).toBeOnTheScreen();
    expect(screen.queryByTestId("mock-post-1")).toBeNull();

    fireEvent.press(screen.getByLabelText("Article posts"));
    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(0, 5, "ARTICLE")
    );
    expect(await screen.findByTestId("mock-post-1")).toBeOnTheScreen();
    expect(screen.queryByTestId("mock-post-3")).toBeNull();

    fireEvent.press(screen.getByLabelText("Article posts"));
    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(0, 5, undefined)
    );
    expect(await screen.findByTestId("mock-post-1")).toBeOnTheScreen();
    expect(screen.getByTestId("mock-post-3")).toBeOnTheScreen();
  });

  it("keeps the selected type when loading the next filtered page", async () => {
    const firstVideoPage = Array.from({ length: 5 }, (_, index) => ({
      ...videoPost,
      id: 20 + index,
      summary: `Video page one ${index}`,
    }));
    const nextVideo = { ...videoPost, id: 30, summary: "Video page two" };
    mockGetFeed.mockImplementation(
      (page: number, _size: number, type?: "VIDEO" | "ARTICLE") => {
        if (type === "VIDEO" && page === 0) return Promise.resolve(firstVideoPage);
        if (type === "VIDEO" && page === 1) return Promise.resolve([nextVideo]);
        return Promise.resolve(posts);
      }
    );

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });
    await screen.findByTestId("mock-post-20");
    fireEvent(screen.UNSAFE_getByType(FlatList), "onEndReached");

    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(1, 5, "VIDEO")
    );
    expect(await screen.findByTestId("mock-post-30")).toBeOnTheScreen();
  });
});
