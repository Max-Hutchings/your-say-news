import React from "react";
import { Linking, Pressable, StyleSheet, Text, View } from "react-native";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import type { PostSource } from "../types";

export function PostSources({ sources }: { sources: PostSource[] }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  if (sources.length === 0) return null;

  return (
    <View testID="post-sources" style={[styles.container, { borderTopColor: e.border }]}>
      <Text style={[styles.heading, { color: e.muted }]}>SOURCES</Text>
      {sources.map((source, index) => (
        <Pressable
          key={`${source.url}-${index}`}
          accessibilityRole="link"
          accessibilityLabel={`Open source ${source.title}`}
          onPress={() => void Linking.openURL(source.url)}
          style={styles.source}
        >
          <Text style={[styles.number, { color: e.teal }]}>{index + 1}</Text>
          <View style={styles.copy}>
            <Text style={[styles.title, { color: e.ink }]}>{source.title}</Text>
            <Text style={[styles.publisher, { color: e.muted }]}>{source.publisher}</Text>
          </View>
        </Pressable>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderTopWidth: 1, marginTop: 16, paddingTop: 12, gap: 9 },
  heading: { fontFamily: EditorialFont.monoSemiBold, fontSize: 9, letterSpacing: 1.2 },
  source: { flexDirection: "row", gap: 9, alignItems: "flex-start" },
  number: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10, paddingTop: 2 },
  copy: { flex: 1 },
  title: { fontFamily: EditorialFont.serifRegular, fontSize: 14, lineHeight: 18, textDecorationLine: "underline" },
  publisher: { fontFamily: EditorialFont.mono, fontSize: 9, marginTop: 2 },
});
