import React, { useState } from "react";
import { View, Text, TextInput, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * Pepper AI compose mode (design handoff) — TEMPLATE ONLY. Pepper researches a
 * topic across sources and drafts the post's fields for review. The prompt box,
 * "Research & write" action and the suggested support question are laid out to
 * match the design but are intentionally NOT wired to any API or logic yet.
 */
export function PepperCompose() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [prompt, setPrompt] = useState("");

  return (
    <View style={styles.container}>
      <Text style={[styles.label, { color: e.muted }]}>PROMPT</Text>
      <View style={[styles.promptBox, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
        <View style={styles.promptIntro}>
          <View style={[styles.avatar, { backgroundColor: e.lime }]}>
            <Text style={[styles.avatarGlyph, { color: e.onLime }]}>✦</Text>
          </View>
          <Text style={[styles.introText, { color: e.ink }]}>
            Give Pepper a topic to research, collect from various sources, and write your post for
            you.
          </Text>
        </View>

        <TextInput
          value={prompt}
          onChangeText={setPrompt}
          placeholder="e.g. The impact of four-day work weeks on productivity and hiring…"
          placeholderTextColor={e.muted}
          multiline
          style={[styles.promptInput, { backgroundColor: e.bg, borderColor: e.border, color: e.secondary }]}
        />

        <View style={styles.promptFooter}>
          <Text style={[styles.footerNote, { color: e.chipText }]}>SCANS UP TO 8 SOURCES</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Research and write"
            style={[styles.cta, { backgroundColor: e.lime }]}
          >
            <Text style={[styles.ctaText, { color: e.onLime }]}>Research &amp; write  →</Text>
          </Pressable>
        </View>
      </View>

      <View style={styles.labelRow}>
        <Text style={[styles.label, { color: e.muted }]}>SUPPORT QUESTION </Text>
        <Text style={[styles.label, { color: e.chipText }]}>· PEPPER SUGGESTS</Text>
      </View>
      <View style={[styles.suggestBlock, { backgroundColor: e.bg, borderColor: e.border }]}>
        <View style={styles.quoteRow}>
          <Text style={[styles.quoteMark, { color: e.lime }]}>{"“"}</Text>
          <Text style={[styles.suggestText, { color: e.ink }]}>
            Pepper&apos;s suggested motion appears here after it drafts your post.
          </Text>
        </View>
        <View style={styles.voteRow}>
          <View style={[styles.votePill, { borderColor: e.teal }]}>
            <Text style={[styles.votePillText, { color: e.teal }]}>AGREE</Text>
          </View>
          <View style={[styles.votePill, { borderColor: e.coral }]}>
            <Text style={[styles.votePillText, { color: e.coral }]}>DISAGREE</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  label: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 1.4,
  },
  labelRow: {
    flexDirection: "row",
    marginTop: 8,
  },
  promptBox: {
    borderRadius: 14,
    borderWidth: 1.5,
    padding: 15,
  },
  promptIntro: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 9,
    marginBottom: 12,
  },
  avatar: {
    width: 26,
    height: 26,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarGlyph: {
    fontSize: 13,
  },
  introText: {
    flex: 1,
    fontFamily: EditorialFont.serifRegular,
    fontSize: 16,
    lineHeight: 22,
  },
  promptInput: {
    minHeight: 66,
    borderRadius: 11,
    borderWidth: 1,
    padding: 12,
    fontFamily: EditorialFont.sans,
    fontSize: 13.5,
    lineHeight: 20,
    textAlignVertical: "top",
  },
  promptFooter: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 11,
  },
  footerNote: {
    fontFamily: EditorialFont.mono,
    fontSize: 9,
    letterSpacing: 0.8,
  },
  cta: {
    height: 32,
    paddingHorizontal: 15,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  ctaText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 12.5,
  },
  suggestBlock: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 15,
  },
  quoteRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  quoteMark: {
    fontFamily: EditorialFont.serif,
    fontSize: 30,
    lineHeight: 24,
  },
  suggestText: {
    flex: 1,
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 16.5,
    lineHeight: 21,
  },
  voteRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 13,
  },
  votePill: {
    flex: 1,
    height: 30,
    borderRadius: 9,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  votePillText: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.8,
  },
});
