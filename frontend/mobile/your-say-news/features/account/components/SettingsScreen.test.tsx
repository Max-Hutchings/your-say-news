import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SettingsScreen } from "./SettingsScreen";

const mockBack = jest.fn();
jest.mock("expo-router", () => ({
  useRouter: () => ({ back: mockBack }),
}));
jest.mock("@expo/vector-icons", () => ({ Ionicons: () => null }));

beforeEach(() => jest.clearAllMocks());

test("marks the active theme and allows the reader to switch themes", () => {
  render(
    <ThemeProvider initialColorScheme="light">
      <SettingsScreen />
    </ThemeProvider>,
  );

  expect(screen.getByRole("radio", { name: "Light" }).props.accessibilityState).toEqual({
    selected: true,
  });
  expect(screen.getByRole("radio", { name: "Dark" }).props.accessibilityState).toEqual({
    selected: false,
  });

  fireEvent.press(screen.getByRole("radio", { name: "Dark" }));

  expect(screen.getByRole("radio", { name: "Dark" }).props.accessibilityState).toEqual({
    selected: true,
  });
  expect(screen.getByRole("radio", { name: "Light" }).props.accessibilityState).toEqual({
    selected: false,
  });

  fireEvent.press(screen.getByLabelText("Back"));
  expect(mockBack).toHaveBeenCalledTimes(1);
});
