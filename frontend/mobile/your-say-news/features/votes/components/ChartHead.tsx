import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * The header above a breakdown chart: the axis name in serif on the left, a mono caption on the
 * right describing what the current view emphasises (e.g. "Number of votes"). Shared by every
 * chart view so they read as one sheet.
 */
export function ChartHead({ title, caption }: { title: string; caption: string }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={styles.row}>
      <Text style={[styles.title, { color: e.ink }]}>{title}</Text>
      <Text style={[styles.caption, { color: e.muted }]}>{caption}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "baseline",
    marginBottom: 14,
  },
  title: {
    fontFamily: EditorialFont.serif,
    fontSize: 17,
  },
  caption: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
});
