import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * Marks a post as produced by the unbiased-post agent (Stage 6). Only ever
 * rendered when a post's `isUnbiased` flag is true — never decorative. Editorial
 * design handoff: a small ink pill with a lime "scales" glyph and mono label.
 */
export function UnbiasedBadge() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={[styles.badge, { backgroundColor: e.ink }]}>
      <Text style={[styles.glyph, { color: e.lime }]}>⚖</Text>
      <Text style={[styles.label, { color: e.bg }]}>UNBIASED</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    gap: 4,
    paddingVertical: 3,
    paddingHorizontal: 7,
    borderRadius: 6,
  },
  glyph: {
    fontSize: 9,
    lineHeight: 11,
  },
  label: {
    fontFamily: EditorialFont.monoSemiBold,
    fontSize: 8.5,
    fontWeight: "600",
    letterSpacing: 1.2,
  },
});
