import React from "react";
import { Pressable, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * One selectable characteristic-axis chip in the results selector. Editorial palette (paper/ink,
 * ink-fill when selected) so it sits with the rest of the sentiment view rather than the legacy
 * onboarding chip. Selected inverts to an ink fill with paper text.
 */
export function AxisChip({
  label,
  selected,
  onPress,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[
        styles.chip,
        {
          backgroundColor: selected ? e.ink : e.chipBg,
          borderColor: selected ? e.ink : e.chipBorder,
        },
      ]}
    >
      <Text style={[styles.label, { color: selected ? e.bg : e.chipText }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1.5,
    marginRight: 8,
  },
  label: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 13,
  },
});
