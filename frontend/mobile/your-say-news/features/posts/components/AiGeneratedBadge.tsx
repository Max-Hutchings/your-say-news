import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/** Factual provenance label, rendered only from the server-owned AI flag. */
export function AiGeneratedBadge() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={[styles.badge, { backgroundColor: e.ink }]}>
      <Text style={[styles.glyph, { color: e.lime }]}>✦</Text>
      <Text style={[styles.label, { color: e.bg }]}>AI GENERATED</Text>
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
  glyph: { fontSize: 9, lineHeight: 11 },
  label: {
    fontFamily: EditorialFont.monoSemiBold,
    fontSize: 8.5,
    fontWeight: "600",
    letterSpacing: 1.1,
  },
});
