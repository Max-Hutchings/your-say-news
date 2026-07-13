import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { FeedTypeFilters } from "./FeedTypeFilters";

describe("FeedTypeFilters", () => {
  it("selects either type and clears the filter when the selected button is pressed again", () => {
    const onChange = jest.fn();
    const view = render(
      <ThemeProvider>
        <FeedTypeFilters value={null} onChange={onChange} />
      </ThemeProvider>
    );

    expect(screen.getByLabelText("Video posts").props.accessibilityState.selected).toBe(false);
    expect(screen.getByLabelText("Article posts").props.accessibilityState.selected).toBe(false);

    fireEvent.press(screen.getByLabelText("Video posts"));
    expect(onChange).toHaveBeenLastCalledWith("VIDEO");

    view.rerender(
      <ThemeProvider>
        <FeedTypeFilters value="VIDEO" onChange={onChange} />
      </ThemeProvider>
    );
    expect(screen.getByLabelText("Video posts").props.accessibilityState.selected).toBe(true);

    fireEvent.press(screen.getByLabelText("Video posts"));
    expect(onChange).toHaveBeenLastCalledWith(null);
    fireEvent.press(screen.getByLabelText("Article posts"));
    expect(onChange).toHaveBeenLastCalledWith("ARTICLE");

    view.rerender(
      <ThemeProvider>
        <FeedTypeFilters value="ARTICLE" onChange={onChange} />
      </ThemeProvider>
    );
    expect(screen.getByLabelText("Article posts").props.accessibilityState.selected).toBe(true);
    fireEvent.press(screen.getByLabelText("Article posts"));
    expect(onChange).toHaveBeenLastCalledWith(null);
  });
});
