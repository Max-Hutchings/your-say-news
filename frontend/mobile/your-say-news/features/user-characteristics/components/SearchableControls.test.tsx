import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SearchableMultiSelect } from "./SearchableMultiSelect";
import { SearchableSelect } from "./SearchableSelect";

const countries = [
  { label: "Canada", value: "CANADA" },
  { label: "India", value: "INDIA" },
  { label: "United Kingdom", value: "UNITED_KINGDOM" },
  { label: "United States", value: "UNITED_STATES" },
];

function themed(ui: React.ReactElement) {
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

test("single select filters options, reports the match count, and closes after selection", () => {
  const onSelect = jest.fn();
  themed(
    <SearchableSelect
      label="Country of residence"
      options={countries}
      selected={null}
      onSelect={onSelect}
    />,
  );

  fireEvent.press(screen.getByText("Select an option"));
  expect(screen.getByText("4 OF 4 MATCHES")).toBeTruthy();

  fireEvent.changeText(screen.getByPlaceholderText("Search…"), "united");
  expect(screen.getByText("2 OF 4 MATCHES")).toBeTruthy();
  expect(screen.queryByText("Canada")).toBeNull();

  fireEvent.press(screen.getByText("United Kingdom"));

  expect(onSelect).toHaveBeenCalledWith("UNITED_KINGDOM");
  expect(screen.queryByPlaceholderText("Search…")).toBeNull();
});

test("single select displays the selected label and check mark", () => {
  themed(
    <SearchableSelect
      label="Country of residence"
      options={countries}
      selected="INDIA"
      onSelect={jest.fn()}
    />,
  );

  fireEvent.press(screen.getByText("India"));

  expect(screen.getByText("✓")).toBeTruthy();
});

test("multi-select displays selected labels, toggles rows, and stays open until Done", () => {
  const onToggle = jest.fn();
  themed(
    <SearchableMultiSelect
      label="Nationalities"
      options={countries}
      selected={["CANADA", "INDIA"]}
      onToggle={onToggle}
    />,
  );

  fireEvent.press(screen.getByText("Canada, India"));
  expect(screen.getByText("2 SELECTED · 4 OF 4")).toBeTruthy();
  expect(screen.getAllByText("✓")).toHaveLength(2);

  fireEvent.press(screen.getByText("United States"));
  expect(onToggle).toHaveBeenCalledWith("UNITED_STATES");
  expect(screen.getByPlaceholderText("Search…")).toBeTruthy();

  fireEvent.changeText(screen.getByPlaceholderText("Search…"), "can");
  expect(screen.getByText("2 SELECTED · 1 OF 4")).toBeTruthy();
  fireEvent.press(screen.getByText("DONE"));
  expect(screen.queryByPlaceholderText("Search…")).toBeNull();
});

test("multi-select uses its placeholder when no values are selected", () => {
  themed(
    <SearchableMultiSelect
      label="Nationalities"
      options={countries}
      selected={[]}
      onToggle={jest.fn()}
    />,
  );
  expect(screen.getByText("Select all that apply")).toBeTruthy();
});
