import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { VoteControls } from "./VoteControls";
import { castVote, getMyVote } from "../services/VoteService";

jest.mock("../services/VoteService");
jest.mock("./SentimentResults", () => ({ SentimentResults: () => null }));
const mockCast = castVote as jest.Mock;
const mockGetMine = getMyVote as jest.Mock;
const binaryOptions = [
  { id: 71, label: "Agree", ordinal: 0, semanticKey: "AGREE" as const },
  { id: 72, label: "Disagree", ordinal: 1, semanticKey: "DISAGREE" as const },
];
const multipleOptions = [
  { id: 73, label: "More frequent buses", ordinal: 0, semanticKey: null },
  { id: 74, label: "Protected cycle lanes", ordinal: 1, semanticKey: null },
  { id: 75, label: "Lower parking charges", ordinal: 2, semanticKey: null },
];

function renderControls(votingType: "BINARY" | "MULTIPLE_CHOICE" = "BINARY") {
  return render(<ThemeProvider><VoteControls postId={7} votingType={votingType}
    options={votingType === "BINARY" ? binaryOptions : multipleOptions}
    supportQuestion="Which transport change should happen first?" /></ThemeProvider>);
}
beforeEach(() => jest.clearAllMocks());

describe("VoteControls", () => {
  it("keeps binary Agree/Disagree and submits the server option id", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 1, postId: 7, optionId: 71 });
    renderControls();
    await waitFor(() => expect(screen.getByTestId("vote-agree").props.accessibilityState.disabled).toBe(false));
    fireEvent.press(screen.getByTestId("vote-agree"));
    await waitFor(() => expect(screen.getByText("You voted — Agree")).toBeOnTheScreen());
    expect(mockCast).toHaveBeenCalledWith(7, 71);
  });

  it("renders only one entry button for multiple choice, then confirms the selected id", async () => {
    mockGetMine.mockResolvedValue(null);
    mockCast.mockResolvedValue({ id: 2, postId: 7, optionId: 74 });
    renderControls("MULTIPLE_CHOICE");
    await waitFor(() => expect(screen.getByTestId("vote-multiple-choice").props.accessibilityState.disabled).toBe(false));
    expect(screen.queryByText("Agree")).toBeNull();
    expect(screen.queryByText("Disagree")).toBeNull();
    fireEvent.press(screen.getByText("Have your say..."));
    fireEvent.press(screen.getByRole("radio", { name: "Protected cycle lanes" }));
    fireEvent.press(screen.getByRole("button", { name: "Submit choice" }));
    await waitFor(() => expect(mockCast).toHaveBeenCalledWith(7, 74));
    expect(await screen.findByText("You chose — Protected cycle lanes")).toBeOnTheScreen();
  });

  it("shows the truthful stored choice on an already-voted card", async () => {
    mockGetMine.mockResolvedValue({ id: 2, postId: 7, optionId: 75 });
    renderControls("MULTIPLE_CHOICE");
    expect(await screen.findByText("You chose — Lower parking charges")).toBeOnTheScreen();
    fireEvent.press(screen.getByTestId("vote-multiple-choice"));
    expect(mockCast).not.toHaveBeenCalled();
  });
});
