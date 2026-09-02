import React from "react";
import { StyleSheet } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PepperCompose } from "./PepperCompose";
import type { PepperDraftRecord } from "../types";

const mockGenerate = jest.fn();
const mockChangeDraft = jest.fn();
let mockHookState: Record<string, unknown>;

jest.mock("../hooks/use-pepper-draft", () => ({
  usePepperDraft: () => mockHookState,
}));

const restoredDraft: PepperDraftRecord = {
  id: "draft-41",
  prompt: "Compare four-day week evidence",
  status: "FINISHED",
  success: true,
  version: 2,
  publishedPostId: null,
  errorMessage: null,
  replicaId: "replica-a",
  content: {
    summary: "Trials generally maintained productivity.",
    supportQuestion: "Should more employers trial a four-day week?",
    caseFor: "Retention may improve.",
    caseAgainst: "Coverage may cost more.",
    votingType: "BINARY",
    voteOptions: ["Agree", "Disagree"],
    citations: [
      { url: "https://www.ons.gov.uk/work", title: "Working patterns", publisher: "ONS" },
      { url: "https://www.acas.org.uk/hours", title: "Working hours", publisher: "Acas" },
    ],
  },
};

beforeEach(() => {
  jest.clearAllMocks();
  mockHookState = {
    draft: null,
    status: "IDLE",
    loading: false,
    error: null,
    generate: mockGenerate,
    changeDraft: mockChangeDraft,
  };
});

function themed(onDraftChange = jest.fn()) {
  return render(
    <ThemeProvider>
      <PepperCompose onDraftChange={onDraftChange} />
    </ThemeProvider>,
  );
}

it("expands the prompt writing area while focused and returns to its compact size on blur", () => {
  themed();
  const prompt = screen.getByPlaceholderText(
    "e.g. The impact of four-day work weeks on productivity and hiring…",
  );

  expect(StyleSheet.flatten(prompt.props.style)).toMatchObject({ minHeight: 96 });
  fireEvent(prompt, "focus");
  expect(StyleSheet.flatten(prompt.props.style)).toMatchObject({ minHeight: 420 });
  fireEvent(prompt, "blur");
  expect(StyleSheet.flatten(prompt.props.style)).toMatchObject({ minHeight: 96 });
});

it("sends the entered prompt to the real generation hook", () => {
  themed();
  const prompt = screen.getByPlaceholderText(
    "e.g. The impact of four-day work weeks on productivity and hiring…",
  );
  fireEvent.changeText(prompt, "Compare evidence on congestion charging");
  fireEvent.press(screen.getByRole("button", { name: "Research and write" }));

  expect(mockGenerate).toHaveBeenCalledWith("Compare evidence on congestion charging");
});

it("restores a complete draft into editable post fields and allows citations to be removed", () => {
  const onDraftChange = jest.fn();
  mockHookState = { ...mockHookState, draft: restoredDraft, status: "FINISHED" };
  themed(onDraftChange);

  expect(screen.getByLabelText("Support question").props.value).toBe(
    "Should more employers trial a four-day week?",
  );
  expect(screen.getByLabelText("Post summary").props.value).toBe(
    "Trials generally maintained productivity.",
  );
  expect(screen.getByLabelText("Case for").props.value).toBe("Retention may improve.");
  expect(screen.getByLabelText("Case against").props.value).toBe("Coverage may cost more.");
  expect(screen.getByText("Agree")).toBeOnTheScreen();
  expect(screen.getByText("Disagree")).toBeOnTheScreen();
  expect(screen.getByText("Working patterns")).toBeOnTheScreen();
  expect(screen.getByText("Working hours")).toBeOnTheScreen();

  fireEvent.changeText(screen.getByLabelText("Post summary"), "Edited balanced summary.");
  expect(mockChangeDraft).toHaveBeenCalledWith(
    expect.objectContaining({ summary: "Edited balanced summary." }),
  );
  expect(onDraftChange).toHaveBeenLastCalledWith({
    ...restoredDraft,
    content: { ...restoredDraft.content, summary: "Edited balanced summary." },
  });

  fireEvent.press(screen.getByRole("button", { name: "Remove citation Working patterns" }));
  expect(mockChangeDraft).toHaveBeenCalledWith(
    expect.objectContaining({
      citations: [{ url: "https://www.acas.org.uk/hours", title: "Working hours", publisher: "Acas" }],
    }),
  );
  expect(onDraftChange).toHaveBeenLastCalledWith({
    ...restoredDraft,
    content: {
      ...restoredDraft.content,
      citations: [{ url: "https://www.acas.org.uk/hours", title: "Working hours", publisher: "Acas" }],
    },
  });
});

it("shows the agreed safe fault copy", () => {
  mockHookState = {
    ...mockHookState,
    status: "FAILED",
    error: "Pepper AI is having trouble, please try again later.",
  };
  themed();

  expect(
    screen.getByText("Pepper AI is having trouble, please try again later."),
  ).toBeOnTheScreen();
});
