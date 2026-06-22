import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, Spacing, BorderRadius, Typography } from "@/constants/theme";

/**
 * Marks a post as produced by the unbiased-post agent (Stage 6). Only ever
 * rendered when a post's `isUnbiased` flag is true — never decorative.
 */
export function UnbiasedBadge() {
  const { colors } = useTheme();

  return (
    <View
      style={[
        styles.badge,
        { backgroundColor: colors.status.infoBackground, borderColor: colors.status.info },
      ]}
    >
      <Text style={[styles.label, { color: colors.status.info }]}>UNBIASED</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: Spacing.sm,
    paddingVertical: Spacing["2xs"],
    borderRadius: BorderRadius.full,
    borderWidth: 1,
  },
  label: {
    ...Typography.overline,
    letterSpacing: 1,
  },
});
