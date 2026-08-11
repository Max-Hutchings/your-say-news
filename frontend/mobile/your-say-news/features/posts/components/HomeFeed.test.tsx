import React from "react";
import { FlatList, RefreshControl } from "react-native";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { HomeFeed } from "./HomeFeed";
import { getFeed } from "../services/PostService";
import type { FeedPage, Post } from "../types";

/** The service always answers with a page envelope; `nextCursor` null means the end of the feed. */
const page = (posts: Post[], nextCursor: string | null = null): FeedPage => ({
  posts,
  nextCursor,
});

const mockPush = jest.fn();

jest.mock("expo-router", () => {
  const React = jest.requireActual("react");
  return {
    useRouter: () => ({ push: mockPush }),
    useFocusEffect: (callback: () => void | (() => void)) => React.useEffect(callback, [callback]),
  };
});

let mockCanPublish = true;

jest.mock("@/features/auth", () => ({
  useAuthStore: (selector: (state: { email: string; canPublish: boolean }) => unknown) =>
    selector({ email: "reader@example.com", canPublish: mockCanPublish }),
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
jest.mock("./FeedTabs", () => {
  const React = jest.requireActual("react");
  const { Pressable, Text } = jest.requireActual("react-native");
  return {
    FeedTabs: ({ onChange }: { onChange: (topicId: string | null) => void }) =>
      React.createElement(Pressable, { accessibilityLabel: "Choose Housing", onPress: () => onChange("housing") },
        React.createElement(Text, null, "Housing")),
  };
});

const mockGetFeed = getFeed as jest.Mock;

const posts: Post[] = [1, 2].map((id) => ({
  id,
  userId: 10 + id,
  summary: `Summary ${id}`,
  supportQuestion: `Support story ${id}?`,
  caseFor: null,
      caseAgainst: null,
      votingType: "BINARY" as const,
      voteOptions: [
        { id: id * 10 + 1, label: "Agree", ordinal: 0, semanticKey: "AGREE" as const },
        { id: id * 10 + 2, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" as const },
      ],
  isUnbiased: false,
  createdAt: "2026-07-13T12:00:00Z",
  media: [],
  topicTags: [],
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
    mockPush.mockReset();
    mockCanPublish = true;
  });

  it("shows the create-post action only to an active publisher", () => {
    // The publishing action is independent of feed loading; keep that request pending so this test
    // observes only the capability-driven render and produces no unrelated async state updates.
    mockGetFeed.mockReturnValue(new Promise<FeedPage>(() => undefined));

    const { rerender } = render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    expect(screen.getByLabelText("New post")).toBeOnTheScreen();
    fireEvent.press(screen.getByLabelText("New post"));
    expect(mockPush).toHaveBeenCalledTimes(1);
    expect(mockPush).toHaveBeenCalledWith("/create-post");

    mockCanPublish = false;
    rerender(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    expect(screen.queryByLabelText("New post")).toBeNull();
  });

  it("scrolls the paged feed to the following post when the active card requests it", async () => {
    mockGetFeed.mockResolvedValue(page(posts));
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
      (_cursor: string | null, _size: number, type?: "VIDEO" | "ARTICLE") => {
        if (type === "VIDEO") return Promise.resolve(page([videoPost]));
        if (type === "ARTICLE") return Promise.resolve(page([posts[0]]));
        return Promise.resolve(page([posts[0], videoPost]));
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
      expect(mockGetFeed).toHaveBeenLastCalledWith(null, 5, "VIDEO", undefined)
    );
    expect(screen.getByLabelText("Video posts").props.accessibilityState.selected).toBe(true);
    expect(await screen.findByTestId("mock-post-3")).toBeOnTheScreen();
    expect(screen.queryByTestId("mock-post-1")).toBeNull();

    fireEvent.press(screen.getByLabelText("Article posts"));
    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(null, 5, "ARTICLE", undefined)
    );
    expect(await screen.findByTestId("mock-post-1")).toBeOnTheScreen();
    expect(screen.queryByTestId("mock-post-3")).toBeNull();

    fireEvent.press(screen.getByLabelText("Article posts"));
    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith(null, 5, undefined, undefined)
    );
    expect(await screen.findByTestId("mock-post-1")).toBeOnTheScreen();
    expect(screen.getByTestId("mock-post-3")).toBeOnTheScreen();
  });

  it("sends the previous page's cursor and the selected type when loading more", async () => {
    const firstVideoPage = Array.from({ length: 5 }, (_, index) => ({
      ...videoPost,
      id: 20 + index,
      summary: `Video page one ${index}`,
    }));
    const nextVideo = { ...videoPost, id: 30, summary: "Video page two" };
    mockGetFeed.mockImplementation(
      (cursor: string | null, _size: number, type?: "VIDEO" | "ARTICLE") => {
        if (type === "VIDEO" && cursor === null) {
          return Promise.resolve(page(firstVideoPage, "cursor-after-24"));
        }
        if (type === "VIDEO" && cursor === "cursor-after-24") {
          return Promise.resolve(page([nextVideo]));
        }
        return Promise.resolve(page(posts));
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
      expect(mockGetFeed).toHaveBeenLastCalledWith("cursor-after-24", 5, "VIDEO", undefined)
    );
    expect(await screen.findByTestId("mock-post-30")).toBeOnTheScreen();
  });

  it("shows a retry message when the feed request fails", async () => {
    mockGetFeed.mockRejectedValue(new Error("network down"));

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });

    expect(
      await screen.findByText("We couldn't load the feed. Pull to try again.")
    ).toBeOnTheScreen();
  });

  it("pull-to-refresh restarts from the first page and re-enables loading more", async () => {
    // The reader starts at the end of the feed (null cursor), so this proves refresh genuinely
    // restarts: it re-requests with no cursor and paging works again from the new cursor.
    mockGetFeed
      .mockResolvedValueOnce(page([videoPost]))
      .mockResolvedValueOnce(page([{ ...videoPost, id: 50 }], "cursor-after-50"))
      .mockResolvedValueOnce(page([{ ...videoPost, id: 51 }]));

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });
    await screen.findByTestId("mock-post-3");

    fireEvent(screen.UNSAFE_getByType(RefreshControl), "refresh");
    await screen.findByTestId("mock-post-50");
    expect(mockGetFeed).toHaveBeenLastCalledWith(null, 5, "VIDEO", undefined);

    fireEvent(screen.UNSAFE_getByType(FlatList), "onEndReached");

    await waitFor(() =>
      expect(mockGetFeed).toHaveBeenLastCalledWith("cursor-after-50", 5, "VIDEO", undefined)
    );
    expect(await screen.findByTestId("mock-post-51")).toBeOnTheScreen();
  });

  it("resets the cursor and refetches when a topic is selected", async () => {
    mockGetFeed
      .mockResolvedValueOnce(page([videoPost], "cursor-before-topic"))
      .mockResolvedValueOnce(page([{ ...videoPost, id: 70, topicTags: [{ id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true }] }], "cursor-in-housing"))
      .mockResolvedValueOnce(page([{ ...videoPost, id: 71, topicTags: [{ id: "housing", label: "Housing", displayGroup: "Society", displayOrder: 6, active: true }] }]));

    render(<ThemeProvider><HomeFeed /></ThemeProvider>);
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });
    await screen.findByTestId("mock-post-3");
    fireEvent.press(screen.getByLabelText("Choose Housing"));

    await waitFor(() => expect(mockGetFeed).toHaveBeenLastCalledWith(null, 5, "VIDEO", "housing"));
    expect(await screen.findByTestId("mock-post-70")).toBeOnTheScreen();

    fireEvent(screen.UNSAFE_getByType(FlatList), "onEndReached");
    await waitFor(() => expect(mockGetFeed).toHaveBeenLastCalledWith("cursor-in-housing", 5, "VIDEO", "housing"));
    expect(await screen.findByTestId("mock-post-71")).toBeOnTheScreen();
  });

  it("stops requesting pages once the service reports the end of the feed", async () => {
    // A full page with a null cursor is the end. Paging on page-length instead would keep asking
    // and render a spinner the reader never gets past.
    const fullPage = Array.from({ length: 5 }, (_, index) => ({
      ...videoPost,
      id: 40 + index,
      summary: `Last page ${index}`,
    }));
    mockGetFeed.mockResolvedValue(page(fullPage));

    render(
      <ThemeProvider>
        <HomeFeed />
      </ThemeProvider>
    );
    fireEvent(screen.getByTestId("home-feed-viewport"), "layout", {
      nativeEvent: { layout: { x: 0, y: 0, width: 390, height: 700 } },
    });
    await screen.findByTestId("mock-post-40");
    fireEvent(screen.UNSAFE_getByType(FlatList), "onEndReached");
    fireEvent(screen.UNSAFE_getByType(FlatList), "onEndReached");

    await waitFor(() => expect(mockGetFeed).toHaveBeenCalledTimes(1));
  });
});
