import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostCard } from "./PostCard";
import type { Post } from "../types";

// The vote controls (from @/features/votes) fetch the caller's vote on mount; stub the votes
// service so PostCard tests stay offline and deterministic. Default: the caller has not voted.
const mockGetMine = jest.fn().mockResolvedValue(null);
const mockCast = jest.fn();
jest.mock("@/features/votes/services/VoteService", () => ({
  getMyVote: (...args: unknown[]) => mockGetMine(...args),
  castVote: (...args: unknown[]) => mockCast(...args),
}));

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
  caseFor: "Protected lanes cut cycling injuries and get more people out of cars.",
  caseAgainst: "Losing a traffic lane will choke deliveries and push congestion onto side streets.",
  isUnbiased: false,
  createdAt: "2026-06-21T10:00:00Z",
  media: [
    {
      mediaType: "IMAGE",
      orientation: "LANDSCAPE",
      s3Key: "posts/abc.jpg",
      contentType: "image/jpeg",
      posterS3Key: null,
      url: "https://s3.local/abc.jpg",
      posterUrl: null,
    },
  ],
};

const portraitPost: Post = {
  ...basePost,
  media: [
    {
      mediaType: "IMAGE",
      orientation: "PORTRAIT",
      s3Key: "posts/tall.jpg",
      contentType: "image/jpeg",
      posterS3Key: null,
      url: "https://s3.local/tall.jpg",
      posterUrl: null,
    },
  ],
};

describe("PostCard", () => {
  beforeEach(() => {
    mockGetMine.mockReset().mockResolvedValue(null);
    mockCast.mockReset();
  });

  it("renders the headline, full summary and support question in place", () => {
    renderWithTheme(<PostCard post={basePost} />);

    expect(screen.getByText("Council approves the new cycle lane")).toBeOnTheScreen();
    expect(screen.getByText(basePost.summary)).toBeOnTheScreen();
    // The motion is shown quoted; match the question text regardless of the surrounding quotes.
    expect(screen.getByText(/Do you agree the cycle lane should go ahead\?/)).toBeOnTheScreen();
  });

  it("offers Agree and Disagree in place (the whole post is shown, no detail screen)", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
  });

  it("keeps the support question and case cards after voting (voting doesn't disrupt the story)", async () => {
    mockCast.mockResolvedValue({ id: 1, postId: basePost.id, voteFor: true });
    renderWithTheme(<PostCard post={basePost} />);

    fireEvent.press(screen.getByTestId("vote-agree"));

    // The vote locks…
    await waitFor(() => expect(screen.getByText("You voted — Agree")).toBeOnTheScreen());
    expect(mockCast).toHaveBeenCalledWith(basePost.id, true);
    // …and the support question + case cards are still shown alongside it.
    expect(screen.getByText(/Do you agree the cycle lane should go ahead\?/)).toBeOnTheScreen();
    expect(screen.getByText("THE CASE FOR")).toBeOnTheScreen();
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
          orientation: "LANDSCAPE",
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

  it("renders the case-for and case-against cards with their arguments", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.getByText("THE CASE FOR")).toBeOnTheScreen();
    expect(screen.getByText("THE CASE AGAINST")).toBeOnTheScreen();
    expect(
      screen.getByText("Protected lanes cut cycling injuries and get more people out of cars.")
    ).toBeOnTheScreen();
    expect(
      screen.getByText(
        "Losing a traffic lane will choke deliveries and push congestion onto side streets."
      )
    ).toBeOnTheScreen();
  });

  it("omits the case cards when the post has no arguments", () => {
    renderWithTheme(<PostCard post={{ ...basePost, caseFor: null, caseAgainst: null }} />);
    expect(screen.queryByText("THE CASE FOR")).toBeNull();
    expect(screen.queryByText("THE CASE AGAINST")).toBeNull();
  });

  it("keeps a landscape post's summary inline with no See more toggle (it all fits)", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.queryByText("See more")).toBeNull();
    expect(screen.queryByText("See less")).toBeNull();
  });

  it("shows a portrait post immersively: headline, support question and vote always visible", () => {
    renderWithTheme(<PostCard post={portraitPost} />);
    // Headline, support question and vote are always visible — never behind See more.
    expect(screen.getByText("Council approves the new cycle lane")).toBeOnTheScreen();
    expect(screen.getByText(/Do you agree the cycle lane should go ahead\?/)).toBeOnTheScreen();
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
    // The summary and case cards are reached via See more; the panel stays mounted so it opens instantly.
    expect(screen.getByText("See more")).toBeOnTheScreen();
    expect(screen.getByText(portraitPost.summary)).toBeOnTheScreen();
    expect(screen.getByText("THE CASE FOR")).toBeOnTheScreen();
  });

  it("toggles the portrait story panel between See more and See less", () => {
    renderWithTheme(<PostCard post={portraitPost} />);
    expect(screen.queryByText("See less")).toBeNull();

    fireEvent.press(screen.getByText("See more"));
    expect(screen.getByText("See less")).toBeOnTheScreen();
    expect(screen.queryByText("See more")).toBeNull();

    fireEvent.press(screen.getByText("See less"));
    expect(screen.getByText("See more")).toBeOnTheScreen();
    expect(screen.queryByText("See less")).toBeNull();
  });
});
