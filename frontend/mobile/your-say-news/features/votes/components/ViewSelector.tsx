import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { SENTIMENT_VIEWS, type SentimentViewKey } from "../data/views";

/**
 * The "View as" segmented control — one tab per chart type in {@link SENTIMENT_VIEWS}. The active
 * tab inverts to an ink fill (matching the selected axis chip), the rest read as quiet labels on a
 * recessed track. Purely presentational: it reports the picked view up and never touches data.
 */
export function ViewSelector({
  view,
  onSelect,
}: {
  view: SentimentViewKey;
  onSelect: (view: SentimentViewKey) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={[styles.track, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
      {SENTIMENT_VIEWS.map((v) => {
        const selected = v.key === view;
        return (
          <Pressable
            key={v.key}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            onPress={() => onSelect(v.key)}
            style={[styles.tab, selected && { backgroundColor: e.ink }]}
          >
            <Text style={[styles.label, { color: selected ? e.bg : e.secondary }]}>{v.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    flexDirection: "row",
    gap: 4,
    padding: 4,
    borderWidth: 1,
    borderRadius: 13,
  },
  tab: {
    flex: 1,
    paddingVertical: 9,
    borderRadius: 9,
    alignItems: "center",
  },
  label: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 12,
  },
});
