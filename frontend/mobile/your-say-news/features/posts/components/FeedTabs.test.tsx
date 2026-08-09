import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { FeedTabs } from "./FeedTabs";

const topics = [
  { id: "politics", label: "Politics", displayGroup: "Politics & government", displayOrder: 1, active: true },
  { id: "economy", label: "Economy", displayGroup: "Money & business", displayOrder: 2, active: true },
  { id: "health", label: "Health", displayGroup: "Society", displayOrder: 3, active: true },
  { id: "technology", label: "Technology", displayGroup: "Science & technology", displayOrder: 4, active: true },
  { id: "energy", label: "Energy", displayGroup: "Climate & environment", displayOrder: 17, active: true },
];

jest.mock("@/features/topics", () => ({ useTopics: () => ({ topics, loading: false, error: null }) }));

it("shows four ordered topics and selects a visible category", () => {
  const onChange = jest.fn();
  render(<ThemeProvider><FeedTabs value={null} onChange={onChange} /></ThemeProvider>);

  expect(screen.getByLabelText("For you").props.accessibilityState.selected).toBe(true);
  expect(screen.getByLabelText("Politics")).toBeOnTheScreen();
  expect(screen.getByLabelText("Technology")).toBeOnTheScreen();
  expect(screen.queryByLabelText("Energy")).toBeNull();
  fireEvent.press(screen.getByLabelText("Health"));
  expect(onChange).toHaveBeenCalledWith("health");
});

it("offers every topic under More and keeps a dropdown selection visible", () => {
  const onChange = jest.fn();
  const view = render(<ThemeProvider><FeedTabs value={null} onChange={onChange} /></ThemeProvider>);

  fireEvent.press(screen.getByLabelText("More ▾"));
  expect(screen.getByText("Climate & environment")).toBeOnTheScreen();
  fireEvent.press(screen.getByLabelText("Energy"));
  expect(onChange).toHaveBeenCalledWith("energy");

  view.rerender(<ThemeProvider><FeedTabs value="energy" onChange={onChange} /></ThemeProvider>);
  expect(screen.getByLabelText("Energy").props.accessibilityState.selected).toBe(true);
  expect(screen.queryByLabelText("Technology")).toBeNull();
});

it("clears the category filter when For you is selected", () => {
  const onChange = jest.fn();
  render(<ThemeProvider><FeedTabs value="health" onChange={onChange} /></ThemeProvider>);

  fireEvent.press(screen.getByLabelText("For you"));
  expect(onChange).toHaveBeenCalledWith(null);
});
