import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * The feed masthead (design handoff): an editorial front-page header — a dated
 * eyebrow line over the "Your Say News" wordmark (lime "Y" mark, a lime
 * highlight under "News"), a 2px ink rule, and the reader's avatar.
 */
export function Masthead({ avatarLabel }: { avatarLabel?: string }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  const today = new Date()
    .toLocaleDateString("en-GB", { weekday: "short", day: "2-digit", month: "short", year: "numeric" })
    .toUpperCase();

  return (
    <View style={styles.container}>
      <View style={styles.dateRow}>
        <Text style={[styles.dateLabel, { color: e.muted }]}>{today}</Text>
        <Text style={[styles.dateLabel, { color: e.muted }]}>ANONYMOUS · AGGREGATE</Text>
      </View>

      <View style={[styles.wordmarkRow, { borderBottomColor: e.ink }]}>
        <View style={styles.brand}>
          <View style={[styles.mark, { backgroundColor: e.lime }]}>
            <Text style={[styles.markLetter, { color: e.onLime }]}>Y</Text>
          </View>
          <Text style={[styles.wordmark, { color: e.ink }]}>Your Say </Text>
          <View style={styles.newsWrap}>
            <View style={[styles.highlight, { backgroundColor: e.lime }]} />
            <Text style={[styles.news, { color: e.ink }]}>News</Text>
          </View>
        </View>

        {avatarLabel ? (
          // Dark mode: the brand primary green with near-black initials; light mode keeps ink-on-lime.
          <View style={[styles.avatar, { backgroundColor: isDark ? e.lime : e.ink }]}>
            <Text style={[styles.avatarLabel, { color: isDark ? e.onLime : e.lime }]}>
              {avatarLabel}
            </Text>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 22,
    paddingTop: 4,
  },
  dateRow: {
    flexDirection: "row",
    justifyContent: "space-between",
  },
  dateLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 9,
    letterSpacing: 1.5,
  },
  wordmarkRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 5,
    paddingBottom: 10,
    borderBottomWidth: 2,
  },
  brand: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  mark: {
    width: 25,
    height: 25,
    borderRadius: 6,
    alignItems: "center",
    justifyContent: "center",
  },
  markLetter: {
    fontFamily: EditorialFont.serif,
    fontSize: 16,
  },
  wordmark: {
    fontFamily: EditorialFont.serif,
    fontSize: 25,
    letterSpacing: -0.25,
  },
  newsWrap: {
    justifyContent: "center",
  },
  highlight: {
    // A thin underline sitting just beneath "News" (not a highlighter through it).
    position: "absolute",
    left: -2,
    right: -2,
    bottom: -2,
    height: 3,
    borderRadius: 1.5,
  },
  news: {
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 25,
  },
  avatar: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarLabel: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 12,
  },
});
