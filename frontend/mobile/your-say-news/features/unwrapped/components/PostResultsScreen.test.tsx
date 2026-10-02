import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostResultsScreen } from "./PostResultsScreen";
import { useUnwrappedFeatures } from "../hooks/use-unwrapped-features";
import { requestUnwrap } from "../services/UnwrappedService";

const mockPush = jest.fn();
const mockBack = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ push: mockPush, back: mockBack }),
}));
jest.mock("../hooks/use-unwrapped-features");
jest.mock("../services/UnwrappedService");
jest.mock("@/features/votes", () => ({
  SentimentResults: ({ postId }: { postId: number }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Text } = require("react-native");
    return <Text>Live results for {postId}</Text>;
  },
}));

const mockFeatures = useUnwrappedFeatures as jest.Mock;
const mockRequestUnwrap = requestUnwrap as jest.Mock;

function renderScreen() {
  return render(<ThemeProvider><PostResultsScreen postId={7} /></ThemeProvider>);
}
beforeEach(() => jest.clearAllMocks());

describe("PostResultsScreen", () => {
  it("shows the vote data under a large Unwrap button", () => {
    mockFeatures.mockReturnValue({ enabled: true, unwrapButton: true });
    renderScreen();
    expect(screen.getByText("How people voted")).toBeOnTheScreen();
    expect(screen.getByText("Live results for 7")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Unwrap" })).toBeOnTheScreen();
  });

  it("asks the backend to unwrap the post before opening Unwrapped", async () => {
    mockFeatures.mockReturnValue({ enabled: true, unwrapButton: true });
    mockRequestUnwrap.mockResolvedValue({ postId: 7, queued: true });
    renderScreen();
    fireEvent.press(screen.getByRole("button", { name: "Unwrap" }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/posts/7/unwrapped"));
    expect(mockRequestUnwrap).toHaveBeenCalledWith(7);
  });

  it("still opens Unwrapped when the unwrap request fails, so an existing story stays reachable", async () => {
    mockFeatures.mockReturnValue({ enabled: true, unwrapButton: true });
    mockRequestUnwrap.mockRejectedValue(new Error("offline"));
    renderScreen();
    fireEvent.press(screen.getByRole("button", { name: "Unwrap" }));

    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/posts/7/unwrapped"));
  });

  it("hides the Unwrap button when Unwrapped is switched off", () => {
    mockFeatures.mockReturnValue({ enabled: false, unwrapButton: true });
    renderScreen();
    expect(screen.getByText("Live results for 7")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Unwrap" })).toBeNull();
  });

  it("hides the Unwrap button while the flags are unknown", () => {
    mockFeatures.mockReturnValue(null);
    renderScreen();
    expect(screen.queryByRole("button", { name: "Unwrap" })).toBeNull();
  });
});
