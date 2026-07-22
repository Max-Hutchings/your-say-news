import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { MultipleChoiceVoteSheet } from "./MultipleChoiceVoteSheet";

const options = [
  { id: 103, label: "More frequent buses", ordinal: 0, semanticKey: null },
  { id: 104, label: "Protected cycle lanes", ordinal: 1, semanticKey: null },
  { id: 105, label: "Lower town-centre parking charges", ordinal: 2, semanticKey: null },
];

function renderSheet(onSubmit = jest.fn(), onClose = jest.fn()) {
  render(
    <ThemeProvider>
      <MultipleChoiceVoteSheet
        visible
        supportQuestion="Which transport change should happen first?"
        options={options}
        submitting={false}
        error={null}
        onSubmit={onSubmit}
        onClose={onClose}
      />
    </ThemeProvider>
  );
  return { onSubmit, onClose };
}

describe("MultipleChoiceVoteSheet", () => {
  it("requires one selection and submits the exact selected option id once", () => {
    const { onSubmit } = renderSheet();

    const submit = screen.getByRole("button", { name: "Submit choice" });
    expect(submit.props.accessibilityState.disabled).toBe(true);

    fireEvent.press(screen.getByRole("radio", { name: "Protected cycle lanes" }));
    expect(screen.getByRole("radio", { name: "Protected cycle lanes" }).props.accessibilityState.selected).toBe(true);
    expect(screen.getByRole("radio", { name: "More frequent buses" }).props.accessibilityState.selected).toBe(false);

    fireEvent.press(submit);
    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith(104);
  });

  it("dismisses without casting and presents the question with all ordered options", () => {
    const { onSubmit, onClose } = renderSheet();

    expect(screen.getByText("Which transport change should happen first?")).toBeOnTheScreen();
    expect(screen.getAllByRole("radio").map((row) => row.props.accessibilityLabel)).toEqual([
      "More frequent buses",
      "Protected cycle lanes",
      "Lower town-centre parking charges",
    ]);

    fireEvent.press(screen.getByLabelText("Close choice sheet"));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("renders the five-option maximum inside a scrollable radio list", () => {
    const five = [
      ...options,
      { id: 106, label: "Fund safer crossings", ordinal: 3, semanticKey: null },
      { id: 107, label: "Improve pavement accessibility", ordinal: 4, semanticKey: null },
    ];
    render(<ThemeProvider><MultipleChoiceVoteSheet visible supportQuestion="Which change first?"
      options={five} submitting={false} error={null} onSubmit={jest.fn()} onClose={jest.fn()} /></ThemeProvider>);
    expect(screen.getAllByRole("radio")).toHaveLength(5);
    expect(screen.getByRole("radio", { name: "Improve pavement accessibility" })).toBeOnTheScreen();
  });

  it("keeps the selected option available for retry after a submission error", () => {
    const props = {
      visible: true,
      supportQuestion: "Which transport change should happen first?",
      options,
      submitting: false,
      error: null as "network" | null,
      onSubmit: jest.fn(),
      onClose: jest.fn(),
    };
    const view = render(<ThemeProvider><MultipleChoiceVoteSheet {...props} /></ThemeProvider>);
    fireEvent.press(screen.getByRole("radio", { name: "Protected cycle lanes" }));

    view.rerender(<ThemeProvider><MultipleChoiceVoteSheet {...props} error="network" /></ThemeProvider>);

    expect(screen.getByRole("radio", { name: "Protected cycle lanes" }).props.accessibilityState.selected).toBe(true);
    expect(screen.getByText("No connection. Try submitting again.")).toBeOnTheScreen();
  });
});
