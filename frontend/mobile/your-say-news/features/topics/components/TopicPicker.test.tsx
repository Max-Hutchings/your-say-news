import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { TopicPicker } from "./TopicPicker";

const mockTopics = ["Politics", "Housing", "Health", "Education"].map((label, index) => ({
  id: label.toLowerCase(), label, displayGroup: index === 0 ? "Politics & government" : "Society", displayOrder: index + 1, active: true,
}));
jest.mock("../hooks/use-topics", () => ({ useTopics: () => ({ topics: mockTopics, loading: false, error: null }) }));

it("blocks a fourth topic while allowing a selected topic to be removed", () => {
  const onChange = jest.fn();
  const view = render(
    <ThemeProvider><TopicPicker value={["politics", "housing", "health"]} onChange={onChange} /></ThemeProvider>,
  );

  expect(screen.getByText("3 / 3")).toBeOnTheScreen();
  expect(screen.getByLabelText("Topic Education").props.accessibilityState.disabled).toBe(true);
  fireEvent.press(screen.getByLabelText("Topic Education"));
  expect(onChange).not.toHaveBeenCalled();

  fireEvent.press(screen.getByLabelText("Topic Housing"));
  expect(onChange).toHaveBeenCalledWith(["politics", "health"]);
  view.unmount();
});

it("adds topics in the order the author selects them", () => {
  const onChange = jest.fn();
  render(<ThemeProvider><TopicPicker value={[]} onChange={onChange} /></ThemeProvider>);

  fireEvent.press(screen.getByLabelText("Topic Politics"));
  expect(onChange).toHaveBeenCalledWith(["politics"]);
});
