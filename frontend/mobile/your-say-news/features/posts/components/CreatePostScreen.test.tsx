import React from "react";
import { TextInput } from "react-native";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
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

  it("publishes only the support question and article summary", async () => {
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
      })
    );
  });
});
