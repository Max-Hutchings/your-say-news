import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { VoteControls } from "./VoteControls";
import { castVote, getMyVote } from "../services/VoteService";

jest.mock("../services/VoteService");
jest.mock("./SentimentResults", () => ({ SentimentResults: () => null }));
const mockCast = castVote as jest.Mock;
const mockGetMine = getMyVote as jest.Mock;

function axiosError(status?: number) {
  return { isAxiosError: true, response: status == null ? undefined : { status } };
}

function renderControls(postId = 7) {
  return render(
    <ThemeProvider>
      <VoteControls postId={postId} />
    </ThemeProvider>
  );
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe("VoteControls", () => {
  it("shows Agree and Disagree for a post the caller has not voted on", async () => {
    mockGetMine.mockResolvedValue(null);

    renderControls();

    await waitFor(() => expect(mockGetMine).toHaveBeenCalledWith(7));
    expect(screen.getByText("Agree")).toBeOnTheScreen();
    expect(screen.getByText("Disagree")).toBeOnTheScreen();
    // Idle voting sits flush at the bottom of the feed card, returning the old blank status-row
    // height to portrait media and landscape story text.
    expect(screen.queryByTestId("vote-status")).toBeNull();
    expect(screen.queryByText(/You voted/)).toBeNull();
  });

  it("casts a vote on press and shows the locked 'You voted' state", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 1, postId: 7, voteFor: true });

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    fireEvent.press(screen.getByTestId("vote-agree"));

    await waitFor(() => expect(screen.getByText("You voted — Agree")).toBeOnTheScreen());
    expect(mockCast).toHaveBeenCalledWith(7, true);
  });

  it("comes up already locked for a post voted on earlier, and ignores further presses", async () => {
    mockGetMine.mockResolvedValue({ id: 2, postId: 7, voteFor: false });

    renderControls();

    await waitFor(() => expect(screen.getByText("You voted — Disagree")).toBeOnTheScreen());

    fireEvent.press(screen.getByTestId("vote-agree"));
    expect(mockCast).not.toHaveBeenCalled();
  });

  it("shows an auth message when the cast is rejected and lets the user retry", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast
      .mockRejectedValueOnce(axiosError(401))
      .mockResolvedValueOnce({ id: 1, postId: 7, voteFor: false });

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    fireEvent.press(screen.getByTestId("vote-agree"));

    await waitFor(() =>
      expect(screen.getByText("Please sign in again to vote.")).toBeOnTheScreen()
    );
    // Not locked — the user can try again and a successful retry opens the data.
    expect(screen.queryByText(/You voted/)).toBeNull();
    fireEvent.press(screen.getByTestId("vote-disagree"));
    await waitFor(() => expect(screen.getByText("You voted — Disagree")).toBeOnTheScreen());
    expect(mockCast).toHaveBeenNthCalledWith(2, 7, false);
    expect(screen.getByText("How others voted")).toBeOnTheScreen();
  });

  it("opens voting data automatically once the vote is recorded", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 1, postId: 7, voteFor: true });

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    // Gated: no results affordance before voting.
    expect(screen.queryByTestId("see-results")).toBeNull();

    fireEvent.press(screen.getByTestId("vote-agree"));

    await waitFor(() => expect(screen.getByTestId("see-results")).toBeOnTheScreen());
    expect(screen.getByText("See how others voted")).toBeOnTheScreen();
    expect(screen.getByText("How others voted")).toBeOnTheScreen();
  });

  it("does not treat a 409 duplicate as an error — it locks to the stored stance", async () => {
    mockGetMine
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ id: 3, postId: 7, voteFor: true });
    mockCast.mockRejectedValue(axiosError(409));

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    fireEvent.press(screen.getByTestId("vote-disagree"));

    await waitFor(() => expect(screen.getByText("You voted — Agree")).toBeOnTheScreen());
    expect(screen.getByText("How others voted")).toBeOnTheScreen();
  });
});
