import React from "react";
import { Pressable, Text } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import {
  DarkTheme,
  LightTheme,
  ThemeProvider,
  useIsDarkMode,
  useTheme,
  useThemeColorScheme,
  useThemeColors,
  useThemedStyles,
} from "./index";

function ThemeProbe() {
  const theme = useTheme();
  const colors = useThemeColors();
  const scheme = useThemeColorScheme();
  const isDark = useIsDarkMode();
  const styles = useThemedStyles((current) => ({
    color: current.colors.text.primary,
  }));

  return (
    <>
      <Text testID="scheme">
        {scheme}:{String(isDark)}:{String(theme.isSystemTheme)}
      </Text>
      <Text testID="ink">{colors.text.primary}</Text>
      <Text testID="styled">{styles.color}</Text>
      <Pressable accessibilityLabel="Toggle theme" onPress={theme.toggleTheme} />
      <Pressable
        accessibilityLabel="Use system theme"
        onPress={() => theme.setColorScheme("system")}
      />
    </>
  );
}

test("provides an explicit light theme and toggles to dark", () => {
  render(
    <ThemeProvider initialColorScheme="light">
      <ThemeProbe />
    </ThemeProvider>,
  );

  expect(screen.getByTestId("scheme")).toHaveTextContent("light:false:false");
  expect(screen.getByTestId("ink")).toHaveTextContent(LightTheme.text.primary);
  expect(screen.getByTestId("styled")).toHaveTextContent(LightTheme.text.primary);

  fireEvent.press(screen.getByLabelText("Toggle theme"));

  expect(screen.getByTestId("scheme")).toHaveTextContent("dark:true:false");
  expect(screen.getByTestId("ink")).toHaveTextContent(DarkTheme.text.primary);
  expect(screen.getByTestId("styled")).toHaveTextContent(DarkTheme.text.primary);
});

test("can return to the current system theme", () => {
  render(
    <ThemeProvider initialColorScheme="dark">
      <ThemeProbe />
    </ThemeProvider>,
  );

  fireEvent.press(screen.getByLabelText("Use system theme"));

  expect(screen.getByTestId("scheme")).toHaveTextContent("light:false:true");
});

test("useTheme rejects components outside the provider", () => {
  function InvalidConsumer() {
    useTheme();
    return null;
  }

  expect(() => render(<InvalidConsumer />)).toThrow(
    "useTheme must be used within a ThemeProvider",
  );
});

test("convenience hooks have safe light defaults outside the provider", () => {
  const warning = jest.spyOn(console, "warn").mockImplementation(() => undefined);

  function FallbackProbe() {
    const colors = useThemeColors();
    return (
      <Text>
        {colors.text.primary}:{useThemeColorScheme()}:{String(useIsDarkMode())}
      </Text>
    );
  }

  render(<FallbackProbe />);

  expect(screen.getByText(`${LightTheme.text.primary}:light:false`)).toBeTruthy();
  expect(warning).toHaveBeenCalledWith(
    "useThemeColors used outside ThemeProvider, defaulting to light theme",
  );
  warning.mockRestore();
});
