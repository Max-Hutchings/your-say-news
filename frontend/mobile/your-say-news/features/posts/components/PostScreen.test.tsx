import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostScreen } from "./PostScreen";
import type { Post } from "../types";

const mockGetPost = jest.fn();
jest.mock("../services/PostService", () => ({
  getPost: (...args: unknown[]) => mockGetPost(...args),
}));

jest.mock("./PostCard", () => ({
  PostCard: ({ post, height }: { post: Post; height: number }) => {
    const { Text } = require("react-native");
    return <Text>{`${post.supportQuestion}:${height}`}</Text>;
  },
}));

const post: Post = {
  id: 7,
  userId: 3,
  authorUsername: "amina.k",
  summary: "The proposal adds protected cycle lanes through the city centre.",
  supportQuestion: "Should the protected cycle lanes go ahead?",
  caseFor: "They can reduce cycling injuries.",
  caseAgainst: "They remove road space.",
  votingType: "BINARY",
  voteOptions: [
    { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" },
    { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" },
  ],
  isAiGenerated: false,
  sources: [],
  createdAt: "2026-06-21T10:00:00Z",
  media: [],
  topicTags: [],
};

function renderScreen(postId = 7) {
  return render(
    <ThemeProvider>
      <PostScreen postId={postId} />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  mockGetPost.mockReset().mockResolvedValue(post);
});

test("loads the shared post and sizes it to the available screen", async () => {
  renderScreen();

  fireEvent(screen.getByTestId("post-screen"), "layout", {
    nativeEvent: { layout: { width: 390, height: 680 } },
  });

  expect(await screen.findByText("Should the protected cycle lanes go ahead?:680")).toBeOnTheScreen();
  expect(mockGetPost).toHaveBeenCalledWith(7);
});

test("shows distinct not-found and request-failure messages", async () => {
  mockGetPost.mockResolvedValueOnce(null);
  const missing = renderScreen(404);
  expect(await screen.findByText("Story not found.")).toBeOnTheScreen();
  missing.unmount();

  mockGetPost.mockRejectedValueOnce(new Error("offline"));
  renderScreen(7);
  expect(await screen.findByText("We couldn't load this story.")).toBeOnTheScreen();
});
