import React from "react";
import { StyleSheet, Text } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { LightTheme, Opacity, Spacing, ThemeProvider } from "@/constants/theme";
import { Card } from "./Card";

function renderCard(card: React.ReactElement) {
  return render(<ThemeProvider initialColorScheme="light">{card}</ThemeProvider>);
}

test.each([
  ["default", LightTheme.surface.primary],
  ["elevated", LightTheme.surface.elevated],
  ["outlined", LightTheme.surface.primary],
  ["ghost", "transparent"],
] as const)("renders the %s visual variant", (variant, backgroundColor) => {
  renderCard(
    <Card testID="card" variant={variant} padding="lg">
      <Text>Analysis</Text>
    </Card>,
  );

  expect(StyleSheet.flatten(screen.getByTestId("card").props.style)).toMatchObject({
    backgroundColor,
    padding: Spacing.xl,
  });
});

test("pressable cards invoke their action and preserve caller styles", () => {
  const onPress = jest.fn();
  renderCard(
    <Card testID="card" pressable onPress={onPress} padding="none" style={{ marginTop: 9 }}>
      <Text>Open story</Text>
    </Card>,
  );

  fireEvent.press(screen.getByText("Open story"));

  expect(onPress).toHaveBeenCalledTimes(1);
  expect(StyleSheet.flatten(screen.getByTestId("card").props.style)).toMatchObject({
    marginTop: 9,
  });
});

test("outlined cards expose the defining border treatment", () => {
  renderCard(
    <Card testID="outlined" variant="outlined">
      <Text>Outlined analysis</Text>
    </Card>,
  );
  expect(StyleSheet.flatten(screen.getByTestId("outlined").props.style)).toMatchObject({
    borderWidth: 1,
    borderColor: LightTheme.border.primary,
  });
});

test("disabled pressable cards are dimmed and cannot be activated", () => {
  const onPress = jest.fn();
  renderCard(
    <Card testID="card" pressable onPress={onPress} disabled>
      <Text>Unavailable</Text>
    </Card>,
  );

  fireEvent.press(screen.getByText("Unavailable"));

  expect(onPress).not.toHaveBeenCalled();
  expect(StyleSheet.flatten(screen.getByTestId("card").props.style)).toMatchObject({
    opacity: Opacity.disabled,
  });
});
