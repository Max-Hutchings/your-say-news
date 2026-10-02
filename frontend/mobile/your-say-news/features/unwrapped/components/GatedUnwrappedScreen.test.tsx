import React from "react";
import { render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { GatedUnwrappedScreen } from "./GatedUnwrappedScreen";
import { useUnwrappedFeatures } from "../hooks/use-unwrapped-features";

jest.mock("expo-router", () => ({
  Redirect: ({ href }: { href: string }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Text } = require("react-native");
    return <Text>Redirected to {href}</Text>;
  },
}));
jest.mock("../hooks/use-unwrapped-features");
jest.mock("./UnwrappedScreen", () => ({
  UnwrappedScreen: ({ postId }: { postId: number }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { Text } = require("react-native");
    return <Text>Unwrapped story for {postId}</Text>;
  },
}));

const mockFeatures = useUnwrappedFeatures as jest.Mock;

function renderGate() {
  return render(<ThemeProvider><GatedUnwrappedScreen postId={7} /></ThemeProvider>);
}

describe("GatedUnwrappedScreen", () => {
  it("opens Unwrapped when it is enabled", () => {
    mockFeatures.mockReturnValue({ enabled: true, unwrapButton: true });
    renderGate();
    expect(screen.getByText("Unwrapped story for 7")).toBeOnTheScreen();
  });

  it("sends a direct link to the results page when Unwrapped is switched off", () => {
    mockFeatures.mockReturnValue({ enabled: false, unwrapButton: true });
    renderGate();
    expect(screen.getByText("Redirected to /posts/7/results")).toBeOnTheScreen();
    expect(screen.queryByText("Unwrapped story for 7")).toBeNull();
  });

  it("shows neither Unwrapped nor a redirect while the flags are still loading", () => {
    mockFeatures.mockReturnValue(null);
    renderGate();
    expect(screen.getByTestId("unwrapped-gate-loading")).toBeOnTheScreen();
    expect(screen.queryByText("Unwrapped story for 7")).toBeNull();
  });
});
