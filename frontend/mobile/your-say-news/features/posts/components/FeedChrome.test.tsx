import React from "react";
import { StyleSheet } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider, getEditorial } from "@/constants/theme";
import { FeedTabs } from "./FeedTabs";
import { Masthead } from "./Masthead";

jest.mock("@/features/topics", () => ({
  useTopicTags: () => ({
    topicTags: [
      { id: "politics", label: "Politics", displayGroup: "Politics & government", displayOrder: 1, active: true },
      { id: "economy", label: "Economy", displayGroup: "Money & business", displayOrder: 2, active: true },
      { id: "health", label: "Health", displayGroup: "Society", displayOrder: 3, active: true },
      { id: "technology", label: "Technology", displayGroup: "Science & technology", displayOrder: 4, active: true },
    ],
  }),
}));

function themed(ui: React.ReactElement, mode: "light" | "dark" = "light") {
  return render(<ThemeProvider initialColorScheme={mode}>{ui}</ThemeProvider>);
}

test("feed tabs present the curated categories with only For you active", () => {
  themed(<FeedTabs value={null} onChange={jest.fn()} />);
  const palette = getEditorial(false);

  for (const label of ["For you", "Politics", "Economy", "Health", "Technology", "More ▾"]) {
    expect(screen.getByText(label)).toBeTruthy();
  }
  expect(StyleSheet.flatten(screen.getByTestId("feed-tab-For you").props.style)).toMatchObject({
    backgroundColor: palette.lime,
    borderColor: palette.lime,
  });
  expect(StyleSheet.flatten(screen.getByTestId("feed-tab-Technology").props.style)).toMatchObject({
    backgroundColor: "transparent",
    borderColor: palette.border,
  });
  for (const label of ["Politics", "Economy", "Health", "Technology", "More ▾"]) {
    expect(StyleSheet.flatten(screen.getByTestId(`feed-tab-${label}`).props.style)).toMatchObject({
      backgroundColor: "transparent",
      borderColor: palette.border,
    });
  }
});

test("masthead shows the date, privacy promise, and interactive account avatar", () => {
  jest.useFakeTimers().setSystemTime(new Date("2026-07-25T10:00:00Z"));
  const onPressAvatar = jest.fn();
  themed(<Masthead avatarLabel="AK" onPressAvatar={onPressAvatar} />);

  expect(screen.getByText("SAT, 25 JUL 2026")).toBeTruthy();
  expect(screen.getByText("ANONYMOUS · AGGREGATE")).toBeTruthy();
  expect(screen.getByText("AK")).toBeTruthy();

  fireEvent.press(screen.getByLabelText("Account"));
  expect(onPressAvatar).toHaveBeenCalledTimes(1);
  jest.useRealTimers();
});

test("masthead omits the account action when no reader label is available", () => {
  themed(<Masthead />);
  expect(screen.queryByLabelText("Account")).toBeNull();
});
