import React, { type ReactNode } from "react";
import { StyleSheet, Text, View } from "react-native";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";

type MarkdownBlock =
  | { type: "paragraph"; text: string }
  | { type: "list"; items: string[] };

export function UnwrappedMarkdown({ text }: { text: string }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={styles.markdown}>
      {parseBlocks(text).map((block, blockIndex) => block.type === "paragraph" ? (
        <Text key={`paragraph-${blockIndex}`} style={[styles.text, { color: e.ink }]}>
          {renderInline(block.text)}
        </Text>
      ) : (
        <View
          key={`list-${blockIndex}`}
          style={styles.list}
          role="list"
          accessible={false}
        >
          {block.items.map((item, itemIndex) => (
            <View
              key={`${itemIndex}-${item}`}
              style={styles.listItem}
              role="listitem"
              accessible
              accessibilityLabel={plainText(item)}
            >
              <Text style={[styles.bullet, { color: e.ink }]}>•</Text>
              <Text style={[styles.text, styles.listText, { color: e.ink }]}>
                {renderInline(item)}
              </Text>
            </View>
          ))}
        </View>
      ))}
    </View>
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
    parts.push(
      <Text key={`${matchIndex}-${match[1]}`} style={styles.bold}>{match[1]}</Text>,
    );
    position = matchIndex + match[0].length;
  }

  if (position < text.length) parts.push(text.slice(position));
  return parts;
}

function plainText(text: string) {
  return text.replace(/\*\*(.+?)\*\*/g, "$1");
}

const styles = StyleSheet.create({
  markdown: { gap: 12 },
  text: { fontFamily: EditorialFont.serifRegular, fontSize: 20, lineHeight: 29 },
  bold: { fontFamily: EditorialFont.serif, fontWeight: "700" },
  list: { gap: 8 },
  listItem: { flexDirection: "row", alignItems: "flex-start", gap: 10 },
  bullet: { fontFamily: EditorialFont.serif, fontSize: 20, lineHeight: 29 },
  listText: { flex: 1 },
});
