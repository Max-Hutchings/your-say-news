import React from "react";
import { render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostCard } from "./PostCard";
import type { Post } from "../types";

// expo-video is a native module; stub the player + a testable VideoView surface.
jest.mock("expo-video", () => ({
  useVideoPlayer: () => ({ play: jest.fn(), pause: jest.fn(), muted: true, loop: true, currentTime: 0 }),
  VideoView: ({ testID }: { testID?: string }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports -- jest.mock factories can't close over imports
    const { View } = require("react-native");
    return <View testID={testID} />;
  },
}));

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

const basePost: Post = {
  id: 7,
  userId: 3,
  title: "Council approves the new cycle lane",
  summary:
    "The plan adds two miles of protected lane through the city centre.\n\nSupporters call it overdue; drivers worry about the lost road space.",
  supportQuestion: "Do you agree the cycle lane should go ahead?",
  isUnbiased: false,
  createdAt: "2026-06-21T10:00:00Z",
  media: [
    {
      mediaType: "IMAGE",
      s3Key: "posts/abc.jpg",
      contentType: "image/jpeg",
      posterS3Key: null,
      url: "https://s3.local/abc.jpg",
      posterUrl: null,
    },
  ],
};

describe("PostCard", () => {
  it("renders the headline, full summary and support question in place", () => {
    renderWithTheme(<PostCard post={basePost} />);

    expect(screen.getByText("Council approves the new cycle lane")).toBeOnTheScreen();
    expect(screen.getByText(basePost.summary)).toBeOnTheScreen();
    expect(screen.getByText("Do you agree the cycle lane should go ahead?")).toBeOnTheScreen();
  });

  it("offers Agree and Disagree in place (the whole post is shown, no detail screen)", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
  });

  it("renders an image from its presigned url", () => {
    renderWithTheme(<PostCard post={basePost} />);
    const media = screen.getByTestId("post-card-media");
    expect(media.props.source).toEqual([{ uri: "https://s3.local/abc.jpg" }]);
  });

  it("renders a video (not an image) when the post carries a clip", () => {
    const videoPost: Post = {
      ...basePost,
      media: [
        {
          mediaType: "VIDEO",
          s3Key: "posts/clip.mp4",
          contentType: "video/mp4",
          posterS3Key: "posts/clip-poster.jpg",
          url: "https://s3.local/clip.mp4",
          posterUrl: "https://s3.local/clip-poster.jpg",
        },
      ],
    };
    renderWithTheme(<PostCard post={videoPost} />);
    expect(screen.getByTestId("post-card-video")).toBeOnTheScreen();
    expect(screen.queryByTestId("post-card-media")).toBeNull();
  });

  it("renders no media when the post has none", () => {
    renderWithTheme(<PostCard post={{ ...basePost, media: [] }} />);
    expect(screen.queryByTestId("post-card-media")).toBeNull();
    expect(screen.queryByTestId("post-card-video")).toBeNull();
  });

  it("hides the unbiased badge when the post is not unbiased", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.queryByText("UNBIASED")).toBeNull();
  });

  it("shows the unbiased badge only when the post is unbiased", () => {
    renderWithTheme(<PostCard post={{ ...basePost, isUnbiased: true }} />);
    expect(screen.getByText("UNBIASED")).toBeOnTheScreen();
  });
});
