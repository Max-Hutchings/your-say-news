import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SentimentResults } from "./SentimentResults";
import { getAxisSentiment, getOverallSentiment } from "../services/SentimentService";

jest.mock("../services/SentimentService");
const mockOverall = getOverallSentiment as jest.Mock;
const mockAxis = getAxisSentiment as jest.Mock;
const options = [
  { id: 31, label: "More frequent buses", ordinal: 0, semanticKey: null },
  { id: 32, label: "Protected cycle lanes", ordinal: 1, semanticKey: null },
  { id: 33, label: "Lower parking charges", ordinal: 2, semanticKey: null },
];
const overall = {
  postId: 3, votingType: "MULTIPLE_CHOICE", characteristic: "OVERALL", options,
  buckets: [{ bucket: "OVERALL", total: 50, choices: [
    { optionId: 31, count: 25, percentage: 50 },
    { optionId: 32, count: 15, percentage: 30 },
    { optionId: 33, count: 10, percentage: 20 },
  ] }], suppressedBuckets: 0,
};
const political = {
  ...overall, characteristic: "politicalPersuasion",
  buckets: [
    { bucket: "LEFT", total: 30, choices: [
      { optionId: 31, count: 18, percentage: 60 }, { optionId: 32, count: 9, percentage: 30 }, { optionId: 33, count: 3, percentage: 10 },
    ] },
    { bucket: "RIGHT", total: 20, choices: [
      { optionId: 31, count: 7, percentage: 35 }, { optionId: 32, count: 6, percentage: 30 }, { optionId: 33, count: 7, percentage: 35 },
    ] },
  ],
};
const age = {
  ...political, characteristic: "ageRange",
  buckets: [{ bucket: "AGE_18_24", total: 12, choices: [
    { optionId: 31, count: 2, percentage: 100 / 6 }, { optionId: 32, count: 7, percentage: 700 / 12 }, { optionId: 33, count: 3, percentage: 25 },
  ] }],
};

function renderResults(onNextPost?: () => void) {
  return render(<ThemeProvider><SentimentResults postId={3} onNextPost={onNextPost} /></ThemeProvider>);
}
beforeEach(() => {
  jest.clearAllMocks();
  mockOverall.mockResolvedValue(overall);
  mockAxis.mockImplementation((_id, axis) => Promise.resolve(axis === "ageRange" ? age : political));
});

describe("SentimentResults", () => {
  it("renders exact option-aware overall and characteristic counts", async () => {
    renderResults();
    expect(await screen.findByText("Overall")).toBeOnTheScreen();
    expect(screen.getByText("50%")).toBeOnTheScreen();
    expect(screen.getByText("25 of 50 votes")).toBeOnTheScreen();
    expect(screen.getAllByText("More frequent buses").length).toBeGreaterThan(0);
    expect(screen.getByText("Left")).toBeOnTheScreen();
    expect(screen.getByText("18")).toBeOnTheScreen();
    expect(screen.getByText("9")).toBeOnTheScreen();
    expect(screen.getByText("3")).toBeOnTheScreen();
  });

  it("shows all N-option values in every chart mode without refetching", async () => {
    renderResults();
    await screen.findByText("Number of votes");
    fireEvent.press(screen.getByRole("button", { name: "Bars" }));
    expect(screen.getByText("Share by option")).toBeOnTheScreen();
    expect(screen.getByText("18 of 30 votes")).toBeOnTheScreen();
    fireEvent.press(screen.getByRole("button", { name: "Table" }));
    expect(screen.getByText("Sorted by total")).toBeOnTheScreen();
    expect(screen.getAllByText("Protected cycle lanes").length).toBeGreaterThan(0);
    fireEvent.press(screen.getByRole("button", { name: "Columns" }));
    expect(screen.getByText("Height = total votes")).toBeOnTheScreen();
    expect(screen.getByText("30")).toBeOnTheScreen();
    expect(mockAxis).toHaveBeenCalledTimes(1);
  });

  it("refetches and renders a different characteristic axis", async () => {
    renderResults();
    await screen.findByText("Left");
    fireEvent.press(screen.getByRole("button", { name: "Age" }));
    await waitFor(() => expect(mockAxis).toHaveBeenCalledWith(3, "ageRange"));
    expect(await screen.findByText("Age 18–24")).toBeOnTheScreen();
    expect(screen.getByText("7")).toBeOnTheScreen();
    expect(screen.queryByText("Left")).toBeNull();
  });

  it("reports suppression and preserves the must-have-voted gate", async () => {
    mockAxis.mockResolvedValueOnce({ ...political, suppressedBuckets: 2 });
    const view = renderResults();
    expect(await screen.findByText("2 small groups hidden to protect privacy.")).toBeOnTheScreen();
    view.unmount();
    mockAxis.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });
    renderResults();
    expect(await screen.findByText("Vote first to see how others voted.")).toBeOnTheScreen();
  });

  it("advances only on a long upward swipe begun at the bottom", async () => {
    const next = jest.fn();
    renderResults(next);
    const scroll = await screen.findByTestId("sentiment-results-scroll");
    fireEvent.scroll(scroll, { nativeEvent: { contentOffset: { y: 576 }, layoutMeasurement: { height: 600 }, contentSize: { height: 1200 } } });
    fireEvent(scroll, "touchStart", { nativeEvent: { pageY: 500 } });
    fireEvent(scroll, "touchEnd", { nativeEvent: { pageY: 453 } });
    expect(next).not.toHaveBeenCalled();
    fireEvent(scroll, "touchStart", { nativeEvent: { pageY: 500 } });
    fireEvent(scroll, "touchEnd", { nativeEvent: { pageY: 452 } });
    expect(next).toHaveBeenCalledTimes(1);
  });
});
