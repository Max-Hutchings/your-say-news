import React from "react";
import type { ReactTestInstance } from "react-test-renderer";
import { Text } from "react-native";
import { render, screen, within } from "@testing-library/react-native";
import { EditorialFont, ThemeProvider } from "@/constants/theme";
import { UnwrappedMarkdown } from "./UnwrappedMarkdown";

function renderMarkdown(text: string) {
  return render(
    <ThemeProvider>
      <UnwrappedMarkdown text={text} />
    </ThemeProvider>,
  );
}

test("visually renders bold text and unordered bullet lists", () => {
  renderMarkdown([
    "**Younger adults**",
    "",
    "Their costs differ because **rent takes more of their income**.",
    "",
    "- Lower monthly costs",
    "- **Reliable transport** for commuting",
  ].join("\n"));

  expect(screen.getByText("Younger adults")).toHaveStyle({
    fontFamily: EditorialFont.serif,
    fontWeight: "700",
  });
  expect(screen.getByText("rent takes more of their income")).toHaveStyle({ fontWeight: "700" });
  expect(screen.getAllByText("•")).toHaveLength(2);
  expect(screen.getByText("Lower monthly costs")).toBeOnTheScreen();
  expect(screen.getByText("Reliable transport")).toHaveStyle({ fontWeight: "700" });
  expect(screen.queryByText(/\*\*/)).toBeNull();
  const list = screen.UNSAFE_getByProps({ role: "list", accessible: false });
  const listItems = within(list).getAllByRole("listitem");
  expect(listItems.map(
    (item) => item.props.accessibilityLabel,
  )).toEqual([
    "Lower monthly costs",
    "Reliable transport for commuting",
  ]);
  for (const item of listItems) {
    expect(within(item).getAllByText("•")).toHaveLength(1);
    expect(textContent(item).startsWith("•")).toBe(true);
  }
  const plainListItem = screen.getByText("Lower monthly costs");
  expect(plainListItem).toHaveStyle({
    fontFamily: EditorialFont.serifRegular,
    fontSize: 20,
    lineHeight: 29,
  });
  expect(plainListItem).not.toHaveStyle({ fontWeight: "700" });
  expect(renderedText()).toEqual([
    "Younger adults",
    "Younger adults",
    "Their costs differ because rent takes more of their income.",
    "rent takes more of their income",
    "Lower monthly costs",
    "Reliable transport for commuting",
    "Reliable transport",
  ]);
});

test("preserves ordinary article text", () => {
  renderMarkdown("This remains an ordinary article paragraph.");

  const paragraph = screen.getByText("This remains an ordinary article paragraph.");
  expect(paragraph).toHaveStyle({
    fontFamily: EditorialFont.serifRegular,
    fontSize: 20,
    lineHeight: 29,
  });
  expect(paragraph).not.toHaveStyle({ fontWeight: "700" });
  expect(screen.queryByText("•")).toBeNull();
});

test("keeps raw HTML as harmless literal text", () => {
  const hostile = '<img src="x" onerror="alert(1)"> <script>alert(2)</script>';
  renderMarkdown(hostile);

  expect(screen.getByText(hostile)).toBeOnTheScreen();
  expect(screen.queryByText("•")).toBeNull();
});

test("handles CRLF, multiple bold ranges and every supported bullet marker", () => {
  renderMarkdown(
    'A **first** and **second** point.\r\n\r\n* Star item\r\n+ Plus item\r\n- Dash item\r\nFollowing paragraph.',
  );

  expect(screen.getByText("first")).toHaveStyle({ fontWeight: "700" });
  expect(screen.getByText("second")).toHaveStyle({ fontWeight: "700" });
  expect(screen.getAllByText("•")).toHaveLength(3);
  const list = screen.UNSAFE_getByProps({ role: "list", accessible: false });
  expect(within(list).queryByText("Following paragraph.")).toBeNull();
  expect(screen.getByText("Following paragraph.")).toBeOnTheScreen();
  expect(renderedText()).toEqual([
    "A first and second point.",
    "first",
    "second",
    "Star item",
    "Plus item",
    "Dash item",
    "Following paragraph.",
  ]);
});

test("renders no text for empty input and preserves unmatched bold markers", () => {
  const { rerender } = renderMarkdown("");

  expect(renderedText()).toEqual([]);

  rerender(
    <ThemeProvider>
      <UnwrappedMarkdown text="An **unfinished marker stays literal." />
    </ThemeProvider>,
  );
  const unmatched = screen.getByText("An **unfinished marker stays literal.");
  expect(unmatched).toBeOnTheScreen();
  expect(unmatched).toHaveStyle({ fontFamily: EditorialFont.serifRegular });
  expect(unmatched).not.toHaveStyle({ fontWeight: "700" });
});

function renderedText() {
  return screen.UNSAFE_queryAllByType(Text)
    .map(textContent)
    .filter((value) => value !== "•");
}

function textContent(node: ReactTestInstance | string): string {
  if (typeof node === "string") return node;
  return node.children.map((child) => textContent(child as ReactTestInstance | string)).join("");
}
