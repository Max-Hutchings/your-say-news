import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import type { FeedPostType } from "../types";

const OPTIONS: {
  value: FeedPostType;
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
}[] = [
  { value: "VIDEO", label: "Video", icon: "videocam-outline" },
  { value: "ARTICLE", label: "Article", icon: "newspaper-outline" },
];

export function FeedTypeFilters({
  value,
  onChange,
}: {
  value: FeedPostType | null;
  onChange: (value: FeedPostType | null) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={styles.row} accessibilityRole="toolbar" accessibilityLabel="Filter posts by type">
      {OPTIONS.map((option) => {
        const selected = value === option.value;
        return (
          <Pressable
            key={option.value}
            testID={`feed-type-${option.value.toLowerCase()}`}
            accessibilityRole="button"
            accessibilityLabel={`${option.label} posts`}
            accessibilityState={{ selected }}
            onPress={() => onChange(selected ? null : option.value)}
            style={[
              styles.button,
              {
                backgroundColor: selected ? e.lime : "transparent",
                borderColor: selected ? e.lime : e.border,
              },
            ]}
          >
            <Ionicons name={option.icon} size={13} color={selected ? e.onLime : e.secondary} />
            <Text style={[styles.label, { color: selected ? e.onLime : e.secondary }]}>
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 7,
    paddingHorizontal: 22,
  },
  button: {
    minHeight: 28,
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
    borderWidth: 1,
  },
  label: {
    fontFamily: EditorialFont.monoMedium,
    fontSize: 10,
  },
});
