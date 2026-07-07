import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SentimentResults } from "./SentimentResults";
import { getOverallSentiment, getAxisSentiment } from "../services/SentimentService";

jest.mock("../services/SentimentService");
const mockOverall = getOverallSentiment as jest.Mock;
const mockAxis = getAxisSentiment as jest.Mock;

// Overall is deliberately 72% so it never collides with any bucket's percentage below.
const OVERALL = {
  postId: 3,
  characteristic: "OVERALL",
  buckets: [{ bucket: "OVERALL", yesCount: 36, noCount: 14, total: 50, yesPct: 72.0, noPct: 28.0 }],
  suppressedBuckets: 0,
};

const POLITICAL = {
  postId: 3,
  characteristic: "politicalPersuasion",
  buckets: [
    { bucket: "LEFT", yesCount: 40, noCount: 10, total: 50, yesPct: 80.0, noPct: 20.0 },
    { bucket: "RIGHT", yesCount: 5, noCount: 15, total: 20, yesPct: 25.0, noPct: 75.0 },
  ],
  suppressedBuckets: 0,
};

const AGE = {
  postId: 3,
  characteristic: "ageRange",
  buckets: [
    { bucket: "AGE_18_24", yesCount: 30, noCount: 10, total: 40, yesPct: 75.0, noPct: 25.0 },
    { bucket: "AGE_65_PLUS", yesCount: 2, noCount: 8, total: 10, yesPct: 20.0, noPct: 80.0 },
  ],
  suppressedBuckets: 0,
};

function byAxis(_postId: number, axis: string) {
  if (axis === "politicalPersuasion") return Promise.resolve(POLITICAL);
  if (axis === "ageRange") return Promise.resolve(AGE);
  return Promise.resolve({ postId: 3, characteristic: axis, buckets: [], suppressedBuckets: 0 });
}

function renderResults(postId = 3) {
  return render(
    <ThemeProvider>
      <SentimentResults postId={postId} />
    </ThemeProvider>
  );
}

beforeEach(() => {
  jest.clearAllMocks();
});

describe("SentimentResults", () => {
  it("shows the overall split and the default (political) breakdown", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    mockAxis.mockImplementation(byAxis);

    renderResults();

    // Overall — the summary row.
    await waitFor(() => expect(screen.getByText("Overall")).toBeOnTheScreen());
    expect(screen.getByText("72%")).toBeOnTheScreen();
    expect(screen.getByText("36 agree · 14 disagree · 50 voted")).toBeOnTheScreen();

    // Default axis is political leaning — largest bucket (Left) first.
    expect(mockAxis).toHaveBeenCalledWith(3, "politicalPersuasion");
    expect(screen.getByText("Left")).toBeOnTheScreen();
    expect(screen.getByText("80%")).toBeOnTheScreen();
    expect(screen.getByText("40 agree · 10 disagree · 50 voted")).toBeOnTheScreen();
    expect(screen.getByText("Right")).toBeOnTheScreen();
    expect(screen.getByText("25%")).toBeOnTheScreen();
  });

  it("sums a multi-bucket overall response into one recomputed split", async () => {
    // If the OVERALL endpoint ever returns several buckets, they are summed and the percentage
    // recomputed to one decimal: (20+20) agree of 65 = 40/65 = 61.5% (exercises the decimal path).
    mockOverall.mockResolvedValue({
      postId: 3,
      characteristic: "OVERALL",
      buckets: [
        { bucket: "A", yesCount: 20, noCount: 10, total: 30, yesPct: 66.7, noPct: 33.3 },
        { bucket: "B", yesCount: 20, noCount: 15, total: 35, yesPct: 57.1, noPct: 42.9 },
      ],
      suppressedBuckets: 0,
    });
    mockAxis.mockImplementation(byAxis);

    renderResults();

    await waitFor(() => expect(screen.getByText("Overall")).toBeOnTheScreen());
    expect(screen.getByText("61.5%")).toBeOnTheScreen();
    expect(screen.getByText("40 agree · 25 disagree · 65 voted")).toBeOnTheScreen();
  });

  it("tells the user when small groups were hidden to protect privacy", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    // Political axis loads with two groups suppressed; religion (below) with exactly one.
    mockAxis.mockImplementation((_postId: number, axis: string) =>
      axis === "politicalPersuasion"
        ? Promise.resolve({ ...POLITICAL, suppressedBuckets: 2 })
        : Promise.resolve({ postId: 3, characteristic: axis, buckets: POLITICAL.buckets, suppressedBuckets: 1 })
    );

    renderResults();

    // Plural.
    expect(await screen.findByText("2 small groups hidden to protect privacy.")).toBeOnTheScreen();

    // Singular after switching axis.
    fireEvent.press(screen.getByRole("button", { name: "Religion" }));
    expect(await screen.findByText("1 small group hidden to protect privacy.")).toBeOnTheScreen();
  });

  it("refetches and re-renders the bars when a different axis is picked", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    mockAxis.mockImplementation(byAxis);

    renderResults();
    await waitFor(() => expect(screen.getByText("Left")).toBeOnTheScreen());

    fireEvent.press(screen.getByRole("button", { name: "Age" }));

    await waitFor(() => expect(mockAxis).toHaveBeenCalledWith(3, "ageRange"));
    // Enum buckets are prettified: AGE_18_24 → "Age 18–24", AGE_65_PLUS → "Age 65+".
    expect(await screen.findByText("Age 18–24")).toBeOnTheScreen();
    expect(screen.getByText("75%")).toBeOnTheScreen();
    expect(screen.getByText("Age 65+")).toBeOnTheScreen();
    expect(screen.getByText("20%")).toBeOnTheScreen();
    // The previous axis's buckets are gone.
    expect(screen.queryByText("Left")).toBeNull();
  });

  it("shows an empty message when an axis has no votes to break down", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    mockAxis.mockImplementation(byAxis);

    renderResults();
    await waitFor(() => expect(screen.getByText("Left")).toBeOnTheScreen());

    fireEvent.press(screen.getByRole("button", { name: "Religion" }));

    expect(await screen.findByText("Not enough votes to break this down yet.")).toBeOnTheScreen();
  });

  it("gates behind voting: a 403 surfaces the not-voted message with a retry", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    mockAxis.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });

    renderResults();

    expect(
      await screen.findByText("Vote first to see how others voted.")
    ).toBeOnTheScreen();
    expect(screen.getByText("Try again")).toBeOnTheScreen();
  });

  it("shows a connection message when the breakdown request never reaches the server", async () => {
    mockOverall.mockResolvedValue(OVERALL);
    mockAxis.mockRejectedValue({ isAxiosError: true, response: undefined });

    renderResults();

    expect(await screen.findByText("No connection. Tap to try again.")).toBeOnTheScreen();
  });

  it("shows a loading spinner while the breakdown is in flight", () => {
    mockOverall.mockReturnValue(new Promise(() => {}));
    mockAxis.mockReturnValue(new Promise(() => {}));

    renderResults();

    expect(screen.getByTestId("breakdown-loading")).toBeOnTheScreen();
  });
});
