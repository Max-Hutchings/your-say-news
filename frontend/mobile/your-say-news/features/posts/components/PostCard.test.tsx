import React from "react";
import { render, screen, fireEvent, waitFor, within } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostCard } from "./PostCard";
import type { Post } from "../types";

const mockPush = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ push: mockPush }),
}));

// The vote controls (from @/features/votes) fetch the caller's vote on mount; stub the votes
// service so PostCard tests stay offline and deterministic. Default: the caller has not voted.
const mockGetMine = jest.fn().mockResolvedValue(null);
const mockCast = jest.fn();
jest.mock("@/features/votes/services/VoteService", () => ({
  getMyVote: (...args: unknown[]) => mockGetMine(...args),
  castVote: (...args: unknown[]) => mockCast(...args),
}));

// expo-video is a native module; stub the player + a testable VideoView surface.
const mockUseVideoPlayer = jest.fn();
jest.mock("expo-video", () => ({
  useVideoPlayer: (...args: unknown[]) => {
    mockUseVideoPlayer(...args);
    return {
      play: jest.fn(),
      pause: jest.fn(),
      muted: true,
      loop: true,
      currentTime: 0,
      status: "readyToPlay",
      addListener: jest.fn(() => ({ remove: jest.fn() })),
    };
  },
  VideoView: ({ testID, player }: { testID?: string; player?: unknown }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports -- jest.mock factories can't close over imports
    const { View } = require("react-native");
    return <View testID={testID} player={player} />;
  },
}));

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

const basePost: Post = {
  id: 7,
  userId: 3,
  summary:
    "The plan adds two miles of protected lane through the city centre.\n\nSupporters call it overdue; drivers worry about the lost road space.",
  supportQuestion: "Do you agree the cycle lane should go ahead?",
  caseFor: "Protected lanes cut cycling injuries and get more people out of cars.",
  caseAgainst: "Losing a traffic lane will choke deliveries and push congestion onto side streets.",
  votingType: "BINARY",
  voteOptions: [
    { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
    { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
  ],
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
    mockPush.mockReset();
    mockGetMine.mockReset().mockResolvedValue(null);
    mockCast.mockReset();
    mockUseVideoPlayer.mockReset();
  });

  it("uses the support question as the sole heading above the article", () => {
    renderWithTheme(<PostCard post={basePost} />);

    expect(screen.getByText(basePost.summary)).toBeOnTheScreen();
    expect(screen.getAllByText(/Do you agree the cycle lane should go ahead\?/)).toHaveLength(1);
  });

  it("offers Agree and Disagree in place (the whole post is shown, no detail screen)", () => {
    renderWithTheme(<PostCard post={basePost} />);
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
  });

  it("opens the author's profile from the card", () => {
    renderWithTheme(<PostCard post={basePost} />);
    fireEvent.press(screen.getByLabelText("Open author profile"));
    expect(mockPush).toHaveBeenCalledWith("/profiles/3");
  });

  it("keeps the support question and case cards after voting (voting doesn't disrupt the story)", async () => {
    mockCast.mockResolvedValue({ id: 1, postId: basePost.id, optionId: 71 });
    renderWithTheme(<PostCard post={basePost} />);

    // The vote buttons stay disabled until the on-mount "have I voted?" lookup settles; press
    // only once they're live, or fireEvent swallows the press on the disabled control.
    await waitFor(() =>
      expect(screen.getByTestId("vote-agree").props.accessibilityState.disabled).toBe(false)
    );
    fireEvent.press(screen.getByTestId("vote-agree"));

    // The vote locks…
    await waitFor(() => expect(screen.getByText("You voted — Agree")).toBeOnTheScreen());
    expect(mockCast).toHaveBeenCalledWith(basePost.id, 71);
    // …and the support question + case cards are still shown alongside it.
    expect(screen.getByText(/Do you agree the cycle lane should go ahead\?/)).toBeOnTheScreen();
    expect(screen.getByText("THE CASE FOR")).toBeOnTheScreen();
  });

  it("renders an image from its presigned url", () => {
    renderWithTheme(<PostCard post={basePost} />);
    const mediaStage = screen.getByTestId("post-media-stage");
    const media = within(mediaStage).getByTestId("post-card-media");
    expect(media.props.source).toEqual([{ uri: "https://s3.local/abc.jpg" }]);
    expect(screen.getAllByLabelText("Open author profile")).toHaveLength(1);
    expect(within(mediaStage).getByLabelText("Open author profile")).toHaveStyle({
      position: "absolute",
      right: 12,
      bottom: 12,
    });
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
    const mediaStage = screen.getByTestId("post-media-stage");
    expect(within(mediaStage).getByTestId("post-card-video")).toBeOnTheScreen();
    expect(screen.queryByTestId("post-card-media")).toBeNull();
    expect(mockUseVideoPlayer).toHaveBeenCalledWith(
      "https://s3.local/clip.mp4",
      expect.any(Function)
    );
    expect(screen.getAllByLabelText("Open author profile")).toHaveLength(1);
    expect(within(mediaStage).getByLabelText("Open author profile")).toHaveStyle({
      position: "absolute",
      right: 12,
      bottom: 12,
    });
    expect(within(mediaStage).getByTestId("video-sound-control")).toHaveStyle({ bottom: 52 });
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

  it("uses the space beneath voting to sit both post layouts slightly lower", () => {
    const view = renderWithTheme(<PostCard post={basePost} />);
    expect(screen.getByTestId("post-card-body")).toHaveStyle({ paddingBottom: 8 });

    view.rerender(
      <ThemeProvider>
        <PostCard post={portraitPost} />
      </ThemeProvider>
    );
    expect(screen.getByTestId("post-card-body")).toHaveStyle({ paddingBottom: 8 });
  });

  it("shows a portrait post immersively: support question and vote always visible", () => {
    renderWithTheme(<PostCard post={portraitPost} />);
    const mediaStage = screen.getByTestId("post-media-stage");
    fireEvent(mediaStage, "layout", {
      nativeEvent: { layout: { width: 390, height: 520 } },
    });
    const portraitImage = within(mediaStage).getByTestId("post-card-media");
    expect(portraitImage.props.source).toEqual([{ uri: "https://s3.local/tall.jpg" }]);
    expect(portraitImage).toHaveStyle({ width: 390, height: 520 });
    // The support question and vote are always visible — never behind See more.
    expect(screen.getByText(/Do you agree the cycle lane should go ahead\?/)).toBeOnTheScreen();
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
    expect(within(mediaStage).getByLabelText("Open author profile")).toHaveStyle({
      position: "absolute",
      right: 12,
      bottom: 12,
    });
    // The summary and case cards are reached via See more; the panel stays mounted so it opens
    // instantly, but while collapsed it's hidden from accessibility — so query hidden elements
    // to inspect it and its contents.
    expect(screen.getByText("See more")).toBeOnTheScreen();
    const seeMoreSlot = within(mediaStage).getByTestId("portrait-see-more-slot");
    expect(seeMoreSlot).toHaveStyle({
      position: "absolute",
      left: 0,
      right: 0,
      bottom: 11,
      alignItems: "center",
    });
    expect(within(seeMoreSlot).getByTestId("portrait-see-more")).toHaveStyle({
      alignSelf: "center",
    });
    const panel = screen.getByTestId("portrait-story-panel", { includeHiddenElements: true });
    expect(panel.props.pointerEvents).toBe("none");
    expect(panel.props.accessibilityElementsHidden).toBe(true);
    expect(panel.props.importantForAccessibility).toBe("no-hide-descendants");
    expect(
      screen.getByText(portraitPost.summary, { includeHiddenElements: true })
    ).toBeOnTheScreen();
    expect(screen.getByText("THE CASE FOR", { includeHiddenElements: true })).toBeOnTheScreen();
  });

  it("toggles the portrait story panel between See more and See less", () => {
    renderWithTheme(<PostCard post={portraitPost} />);
    expect(screen.queryByText("See less")).toBeNull();

    fireEvent.press(screen.getByText("See more"));
    expect(screen.getByText("See less")).toBeOnTheScreen();
    expect(screen.queryByText("See more")).toBeNull();
    expect(screen.getByTestId("portrait-story-panel").props.pointerEvents).toBe("auto");
    expect(screen.getByTestId("portrait-story-panel").props.accessibilityElementsHidden).toBe(false);
    expect(screen.getByTestId("portrait-story-panel").props.importantForAccessibility).toBe("auto");

    fireEvent.press(screen.getByText("See less"));
    expect(screen.getByText("See more")).toBeOnTheScreen();
    expect(screen.queryByText("See less")).toBeNull();
  });
});
