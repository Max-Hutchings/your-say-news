import type { ReactNode } from "react";

type MarkdownBlock =
  | { type: "paragraph"; text: string }
  | { type: "list"; items: string[] };

export function UnwrappedMarkdown({ text }: { text: string }) {
  return (
    <div className="unwrapped-markdown">
      {parseBlocks(text).map((block, blockIndex) => block.type === "paragraph" ? (
        <p key={`paragraph-${blockIndex}`}>{renderInline(block.text)}</p>
      ) : (
        <ul key={`list-${blockIndex}`}>
          {block.items.map((item, itemIndex) => (
            <li key={`${itemIndex}-${item}`}>{renderInline(item)}</li>
          ))}
        </ul>
      ))}
    </div>
  );
}

function parseBlocks(markdown: string): MarkdownBlock[] {
  const blocks: MarkdownBlock[] = [];
  let paragraphLines: string[] = [];
  let listItems: string[] = [];

  const flushParagraph = () => {
    if (paragraphLines.length > 0) {
      blocks.push({ type: "paragraph", text: paragraphLines.join(" ") });
      paragraphLines = [];
    }
  };
  const flushList = () => {
    if (listItems.length > 0) {
      blocks.push({ type: "list", items: listItems });
      listItems = [];
    }
  };

  for (const line of markdown.replaceAll("\r\n", "\n").split("\n")) {
    const bullet = line.match(/^\s*[-*+]\s+(.+)$/);
    if (bullet) {
      flushParagraph();
      listItems.push(bullet[1].trim());
    } else if (!line.trim()) {
      flushParagraph();
      flushList();
    } else {
      flushList();
      paragraphLines.push(line.trim());
    }
  }

  flushParagraph();
  flushList();
  return blocks;
}

function renderInline(text: string): ReactNode[] {
  const parts: ReactNode[] = [];
  const boldPattern = /\*\*(.+?)\*\*/g;
  let position = 0;

  for (const match of text.matchAll(boldPattern)) {
    const matchIndex = match.index;
    if (matchIndex > position) parts.push(text.slice(position, matchIndex));
    parts.push(<strong key={`${matchIndex}-${match[1]}`}>{match[1]}</strong>);
    position = matchIndex + match[0].length;
  }

  if (position < text.length) parts.push(text.slice(position));
  return parts;
}
