import React from "react";
import { Alert, TextInput } from "react-native";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { CreatePostScreen } from "./CreatePostScreen";

const mockBack = jest.fn();
const mockSubmit = jest.fn();

jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack }),
}));

jest.mock("../hooks/use-create-post", () => ({
  MAX_IMAGES: 5,
  useCreatePost: () => ({
    picked: [],
    progress: 0,
    submitting: false,
    error: null,
    fieldErrors: {},
    pickMedia: jest.fn(),
    removeMedia: jest.fn(),
    submit: mockSubmit,
  }),
}));

describe("CreatePostScreen", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockSubmit.mockResolvedValue(null);
  });

  it("puts the support question first and has no separate headline field", () => {
    render(
      <ThemeProvider>
        <CreatePostScreen />
      </ThemeProvider>
    );

    const placeholders = screen.UNSAFE_getAllByType(TextInput).map(
      (input) => input.props.placeholder
    );
    expect(placeholders).toEqual([
      "Should phones be banned in schools during class hours?",
      "Give readers the context they need before voting.",
    ]);
    expect(screen.queryByText("HEADLINE")).toBeNull();
  });

  it("publishes the binary default with no authored options or arguments", async () => {
    render(
      <ThemeProvider>
        <CreatePostScreen />
      </ThemeProvider>
    );

    fireEvent.changeText(
      screen.getByPlaceholderText("Should phones be banned in schools during class hours?"),
      "Should every school ban phones?"
    );
    fireEvent.changeText(
      screen.getByPlaceholderText("Give readers the context they need before voting."),
      "Schools report fewer distractions, while parents worry about contact in emergencies."
    );
    fireEvent.press(screen.getByText("Post"));

    await waitFor(() =>
      expect(mockSubmit).toHaveBeenCalledWith({
        supportQuestion: "Should every school ban phones?",
        summary:
          "Schools report fewer distractions, while parents worry about contact in emergencies.",
        votingType: "BINARY",
        voteOptions: ["", ""],
        caseFor: "",
        caseAgainst: "",
      })
    );
  });

  it("keeps multiple-choice text while toggled away and submits the visible accessible order", async () => {
    render(<ThemeProvider><CreatePostScreen /></ThemeProvider>);
    fireEvent.press(screen.getByRole("switch", { name: "Multiple choice" }));
    fireEvent.changeText(screen.getByLabelText("Choice 1"), "More frequent buses");
    fireEvent.changeText(screen.getByLabelText("Choice 2"), "Protected cycle lanes");
    fireEvent.press(screen.getByRole("button", { name: "Add option" }));
    fireEvent.changeText(screen.getByLabelText("Choice 3"), "Lower parking charges");
    fireEvent.press(screen.getByRole("button", { name: "Move choice 3 up" }));

    fireEvent.press(screen.getByRole("switch", { name: "Multiple choice" }));
    expect(screen.queryByLabelText("Choice 1")).toBeNull();
    fireEvent.press(screen.getByRole("switch", { name: "Multiple choice" }));
    expect(screen.getByLabelText("Choice 1").props.value).toBe("More frequent buses");
    expect(screen.getByLabelText("Choice 2").props.value).toBe("Lower parking charges");
    expect(screen.getByLabelText("Choice 3").props.value).toBe("Protected cycle lanes");

    fireEvent.press(screen.getByText("Post"));
    await waitFor(() => expect(mockSubmit).toHaveBeenCalledWith(expect.objectContaining({
      votingType: "MULTIPLE_CHOICE",
      voteOptions: ["More frequent buses", "Lower parking charges", "Protected cycle lanes"],
    })));
  });

  it("asks before hiding and clearing typed supporting arguments", () => {
    const alert = jest.spyOn(Alert, "alert").mockImplementation(() => undefined);
    render(<ThemeProvider><CreatePostScreen /></ThemeProvider>);

    fireEvent.press(screen.getByRole("checkbox", { name: "Add supporting arguments" }));
    fireEvent.changeText(screen.getByLabelText("Case for"), "It improves access");
    fireEvent.press(screen.getByRole("checkbox", { name: "Add supporting arguments" }));

    expect(alert).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Case for").props.value).toBe("It improves access");
    const actions = alert.mock.calls[0][2];
    act(() => actions?.find((action) => action.text === "Remove")?.onPress?.());
    expect(screen.queryByLabelText("Case for")).toBeNull();
  });
});
