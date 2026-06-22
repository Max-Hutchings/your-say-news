import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostCard } from "./PostCard";
import type { Post } from "../types";

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

const basePost: Post = {
  id: 7,
  userId: 3,
  title: "Council approves the new cycle lane",
  summary: "The plan adds two miles of protected lane through the city centre.",
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
  it("renders the headline, summary and support question", () => {
    renderWithTheme(<PostCard post={basePost} />);

    expect(screen.getByText("Council approves the new cycle lane")).toBeOnTheScreen();
    expect(
      screen.getByText("The plan adds two miles of protected lane through the city centre.")
    ).toBeOnTheScreen();
    expect(
      screen.getByText("Do you agree the cycle lane should go ahead?")
    ).toBeOnTheScreen();
  });

  it("renders the lead image from its presigned url", () => {
    renderWithTheme(<PostCard post={basePost} />);
    const media = screen.getByTestId("post-card-media");
    expect(media).toBeOnTheScreen();
    expect(media.props.source).toEqual([{ uri: "https://s3.local/abc.jpg" }]);
  });

  it("renders a video's poster frame, not its raw url", () => {
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
    expect(screen.getByTestId("post-card-media").props.source).toEqual([
      { uri: "https://s3.local/clip-poster.jpg" },
    ]);
  });

  it("renders no media when the post has none", () => {
    renderWithTheme(<PostCard post={{ ...basePost, media: [] }} />);
    expect(screen.queryByTestId("post-card-media")).toBeNull();
  });

  it("hides the unbiased badge when the post is not unbiased", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.queryByText("UNBIASED")).toBeNull();
  });

  it("shows the unbiased badge only when the post is unbiased", () => {
    renderWithTheme(<PostCard post={{ ...basePost, isUnbiased: true }} />);
    expect(screen.getByText("UNBIASED")).toBeOnTheScreen();
  });

  it("calls onPress when the card is pressed", () => {
    const onPress = jest.fn();
    renderWithTheme(<PostCard post={basePost} onPress={onPress} />);

    fireEvent.press(screen.getByText("Council approves the new cycle lane"));

    expect(onPress).toHaveBeenCalledTimes(1);
  });
});
