import React from "react";
import { StyleSheet } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider, LightTheme } from "@/constants/theme";
import { Input } from "./Input";

function renderInput(input: React.ReactElement) {
  return render(<ThemeProvider initialColorScheme="light">{input}</ThemeProvider>);
}

test("renders its label, helper text, and forwards text changes", () => {
  const onChangeText = jest.fn();
  renderInput(
    <Input
      label="Email address"
      helperText="Used only for signing in"
      placeholder="reader@example.org"
      onChangeText={onChangeText}
    />,
  );

  fireEvent.changeText(screen.getByPlaceholderText("reader@example.org"), "amina@example.org");

  expect(screen.getByText("Email address")).toBeTruthy();
  expect(screen.getByText("Used only for signing in")).toBeTruthy();
  expect(onChangeText).toHaveBeenCalledWith("amina@example.org");
});

test("focus and blur use the focused and default border colors", () => {
  const onFocus = jest.fn();
  const onBlur = jest.fn();
  renderInput(
    <Input placeholder="Display name" onFocus={onFocus} onBlur={onBlur} />,
  );
  const input = screen.getByPlaceholderText("Display name");

  expect(StyleSheet.flatten(input.props.style)).toMatchObject({
    borderColor: LightTheme.input.border,
  });
  fireEvent(input, "focus", { nativeEvent: {} });
  expect(StyleSheet.flatten(input.props.style)).toMatchObject({
    borderColor: LightTheme.input.borderFocus,
  });
  expect(onFocus).toHaveBeenCalledTimes(1);

  fireEvent(input, "blur", { nativeEvent: {} });
  expect(StyleSheet.flatten(input.props.style)).toMatchObject({
    borderColor: LightTheme.input.border,
  });
  expect(onBlur).toHaveBeenCalledTimes(1);
});

test("error styling takes priority and disabled filled inputs are not editable", () => {
  renderInput(
    <Input
      error="Enter a valid email address"
      helperText="This should be hidden"
      placeholder="Email"
      variant="filled"
      size="lg"
      disabled
      editable
    />,
  );
  const input = screen.getByPlaceholderText("Email");
  const styles = StyleSheet.flatten(input.props.style);

  expect(input.props.editable).toBe(false);
  expect(styles).toMatchObject({
    borderBottomColor: LightTheme.status.error,
    backgroundColor: LightTheme.surface.tertiary,
  });
  expect(screen.getByText("Enter a valid email address")).toBeTruthy();
  expect(screen.queryByText("This should be hidden")).toBeNull();
});
