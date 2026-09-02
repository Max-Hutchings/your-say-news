import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { UnwrappedMarkdown } from "./UnwrappedMarkdown";

describe("UnwrappedMarkdown", () => {
  it("renders bold text and unordered lists as semantic HTML", () => {
    const { container } = render(
      <UnwrappedMarkdown text={[
        "**Younger adults**",
        "",
        "Their costs differ because **rent takes more of their income**.",
        "",
        "- Lower monthly costs",
        "- **Reliable transport** for commuting",
      ].join("\n")} />,
    );

    expect(screen.getByText("Younger adults").tagName).toBe("STRONG");
    expect(screen.getByText("rent takes more of their income").tagName).toBe("STRONG");

    const list = screen.getByRole("list");
    const items = within(list).getAllByRole("listitem");
    expect(items.map((item) => item.textContent)).toEqual([
      "Lower monthly costs",
      "Reliable transport for commuting",
    ]);
    expect([...container.querySelectorAll("p")].map((paragraph) => paragraph.textContent)).toEqual([
      "Younger adults",
      "Their costs differ because rent takes more of their income.",
    ]);
    const markdown = container.querySelector(".unwrapped-markdown");
    expect([...markdown!.children].map((block) => [block.tagName, block.textContent])).toEqual([
      ["P", "Younger adults"],
      ["P", "Their costs differ because rent takes more of their income."],
      ["UL", "Lower monthly costsReliable transport for commuting"],
    ]);
    expect(within(list).getByText("Reliable transport").tagName).toBe("STRONG");
    expect(screen.queryByText(/\*\*/)).not.toBeInTheDocument();
  });

  it("preserves ordinary text as a paragraph", () => {
    render(<UnwrappedMarkdown text="This remains an ordinary article paragraph." />);

    expect(screen.getByText("This remains an ordinary article paragraph.").tagName).toBe("P");
  });

  it("keeps raw HTML literal rather than creating executable elements", () => {
    const { container } = render(
      <UnwrappedMarkdown text={'<img src="x" onerror="alert(1)">\n<script>alert(2)</script>\n\n- <svg onload="alert(3)">unsafe list item</svg>'} />,
    );

    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("svg")).toBeNull();
    expect(container).toHaveTextContent('<img src="x" onerror="alert(1)"> <script>alert(2)</script>');
    expect(screen.getByRole("listitem")).toHaveTextContent('<svg onload="alert(3)">unsafe list item</svg>');
  });

  it("handles CRLF, multiple bold ranges and every supported bullet marker", () => {
    const { container } = render(
      <UnwrappedMarkdown
        text={'A **first** and **second** point.\r\n\r\n* Star item\r\n+ Plus item\r\n- Dash item\r\nFollowing paragraph.'}
      />,
    );

    expect([...container.querySelectorAll("strong")].map((value) => value.textContent)).toEqual([
      "first",
      "second",
    ]);
    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "Star item",
      "Plus item",
      "Dash item",
    ]);
    const markdown = container.querySelector(".unwrapped-markdown");
    expect([...markdown!.children].map((block) => [block.tagName, block.textContent])).toEqual([
      ["P", "A first and second point."],
      ["UL", "Star itemPlus itemDash item"],
      ["P", "Following paragraph."],
    ]);
  });

  it("renders no blocks for empty input and preserves unmatched bold markers", () => {
    const { container, rerender } = render(<UnwrappedMarkdown text="" />);

    expect(container.firstChild).toBeEmptyDOMElement();

    rerender(<UnwrappedMarkdown text="An **unfinished marker stays literal." />);
    expect(container).toHaveTextContent("An **unfinished marker stays literal.");
    expect(container.querySelector("strong")).toBeNull();
  });
});
