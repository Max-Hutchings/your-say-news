import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { VoteControls } from "./VoteControls";
import { castVote, getMyVote } from "../services/VoteService";

jest.mock("../services/VoteService");
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
    // No locked caption before voting.
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

  it("shows an auth message when the cast is rejected and stays votable", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockRejectedValue(axiosError(401));

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    fireEvent.press(screen.getByTestId("vote-agree"));

    await waitFor(() =>
      expect(screen.getByText("Please sign in again to vote.")).toBeOnTheScreen()
    );
    // Not locked — the user can try again.
    expect(screen.queryByText(/You voted/)).toBeNull();
  });

  it("reveals 'See how others voted' only once the vote is locked", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 1, postId: 7, voteFor: true });

    renderControls();
    await waitFor(() => expect(mockGetMine).toHaveBeenCalled());

    // Gated: no results affordance before voting.
    expect(screen.queryByTestId("see-results")).toBeNull();

    fireEvent.press(screen.getByTestId("vote-agree"));

    await waitFor(() => expect(screen.getByTestId("see-results")).toBeOnTheScreen());
    expect(screen.getByText("See how others voted")).toBeOnTheScreen();
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
    expect(screen.queryByText(/try again/)).toBeNull();
  });
});
