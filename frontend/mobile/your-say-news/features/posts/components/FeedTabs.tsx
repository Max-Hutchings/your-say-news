import React from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * The feed's category strip (from the design handoff): "For you" and topic filters across the top
 * of the feed. Mono-type rounded-rectangle chips — the active one is filled with the brand green
 * (the design's ink fill turns near-white in dark mode, so we use the constant lime instead), the
 * rest are outlined. Presentational only for now: the tabs are inert and "For you" is always active; wiring
 * them to filtered feeds comes later, so they read as "coming soon" rather than a broken control.
 */
const CATEGORIES = ["For you", "AI", "Policy", "Hardware", "Climate"] as const;
const ACTIVE = "For you";

export function FeedTabs() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.row}
    >
      {CATEGORIES.map((label) => {
        const active = label === ACTIVE;
        return (
          <View
            key={label}
            testID={`feed-tab-${label}`}
            style={[
              styles.tab,
              active
                ? { backgroundColor: e.lime, borderColor: e.lime }
                : { backgroundColor: "transparent", borderColor: e.border },
            ]}
          >
            <Text style={[styles.label, { color: active ? e.onLime : e.secondary }]}>{label}</Text>
          </View>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: {
    paddingHorizontal: 22,
    gap: 8,
    alignItems: "center",
  },
  tab: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 8,
    borderWidth: 1,
  },
  label: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
  },
});
