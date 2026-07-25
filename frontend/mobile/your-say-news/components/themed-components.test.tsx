import React from "react";
import { StyleSheet, Text } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { DarkTheme, LightTheme, ThemeProvider, Typography } from "@/constants/theme";
import { ThemedText } from "./themed-text";
import { ThemedView } from "./themed-view";
import { Collapsible } from "./ui/collapsible";

function themed(ui: React.ReactElement, mode: "light" | "dark" = "light") {
  return render(<ThemeProvider initialColorScheme={mode}>{ui}</ThemeProvider>);
}

test.each([
  ["primary", LightTheme.text.primary],
  ["secondary", LightTheme.text.secondary],
  ["success", LightTheme.status.success],
  ["warning", LightTheme.status.warning],
  ["error", LightTheme.status.error],
  ["info", LightTheme.status.info],
] as const)("ThemedText maps %s to the expected palette token", (color, expected) => {
  themed(<ThemedText color={color}>Signal</ThemedText>);
  expect(StyleSheet.flatten(screen.getByText("Signal").props.style)).toMatchObject({
    color: expected,
  });
});

test("ThemedText supports current and legacy typography variants", () => {
  themed(
    <>
      <ThemedText variant="h2">Current heading</ThemedText>
      <ThemedText type="link">Legacy link</ThemedText>
    </>,
  );

  expect(StyleSheet.flatten(screen.getByText("Current heading").props.style)).toMatchObject(
    Typography.h2,
  );
  expect(StyleSheet.flatten(screen.getByText("Legacy link").props.style)).toMatchObject({
    ...Typography.link,
    color: LightTheme.text.link,
  });
});

test("legacy text and view color overrides follow the active scheme", () => {
  const first = themed(
    <>
      <ThemedText lightColor="#112233" darkColor="#ddeeff">Override text</ThemedText>
      <ThemedView testID="override-view" lightColor="#abcdef" darkColor="#123456" />
    </>,
  );
  expect(StyleSheet.flatten(screen.getByText("Override text").props.style)).toMatchObject({
    color: "#112233",
  });
  expect(StyleSheet.flatten(screen.getByTestId("override-view").props.style)).toMatchObject({
    backgroundColor: "#abcdef",
  });
  first.unmount();

  themed(
    <>
      <ThemedText lightColor="#112233" darkColor="#ddeeff">Override text</ThemedText>
      <ThemedView testID="override-view" lightColor="#abcdef" darkColor="#123456" />
    </>,
    "dark",
  );
  expect(StyleSheet.flatten(screen.getByText("Override text").props.style)).toMatchObject({
    color: "#ddeeff",
  });
  expect(StyleSheet.flatten(screen.getByTestId("override-view").props.style)).toMatchObject({
    backgroundColor: "#123456",
  });
});

test.each([
  ["primary", DarkTheme.background.primary],
  ["secondary", DarkTheme.background.secondary],
  ["tertiary", DarkTheme.background.tertiary],
  ["surface", DarkTheme.surface.primary],
  ["elevated", DarkTheme.surface.elevated],
  ["transparent", "transparent"],
] as const)("ThemedView maps %s to the expected dark background", (variant, expected) => {
  themed(
    <ThemedView testID="surface" variant={variant}>
      <Text>Content</Text>
    </ThemedView>,
    "dark",
  );
  expect(StyleSheet.flatten(screen.getByTestId("surface").props.style)).toMatchObject({
    backgroundColor: expected,
  });
});

test("Collapsible reveals and hides its content", () => {
  themed(
    <Collapsible title="How voting works">
      <Text>Votes are reported only in aggregate.</Text>
    </Collapsible>,
  );

  expect(screen.queryByText("Votes are reported only in aggregate.")).toBeNull();
  fireEvent.press(screen.getByText("How voting works"));
  expect(screen.getByText("Votes are reported only in aggregate.")).toBeTruthy();
  fireEvent.press(screen.getByText("How voting works"));
  expect(screen.queryByText("Votes are reported only in aggregate.")).toBeNull();
});
