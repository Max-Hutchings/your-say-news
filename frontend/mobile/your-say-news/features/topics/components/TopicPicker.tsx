import React, { useMemo } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { useTopicTags } from "../hooks/use-topics";

type TopicTagPickerProps = {
  value: string[];
  onChange: (topicTagIds: string[]) => void;
  max?: number;
};

/** Grouped optional picker used by post authors. */
export function TopicTagPicker({ value, onChange, max = 3 }: TopicTagPickerProps) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { topicTags, error } = useTopicTags();
  const groups = useMemo(() => {
    const result = new Map<string, typeof topicTags>();
    topicTags.forEach((topicTag) => result.set(topicTag.displayGroup,
      [...(result.get(topicTag.displayGroup) ?? []), topicTag]));
    return [...result.entries()];
  }, [topicTags]);

  const toggle = (topicTagId: string) => {
    if (value.includes(topicTagId)) {
      onChange(value.filter((id) => id !== topicTagId));
    } else if (value.length < max) {
      onChange([...value, topicTagId]);
    }
  };

  return (
    <View style={styles.picker}>
      <View style={styles.heading}>
        <Text style={[styles.title, { color: e.ink }]}>Topic tags</Text>
        <Text style={[styles.count, { color: e.muted }]}>{value.length} / {max}</Text>
      </View>
      <Text style={[styles.help, { color: e.muted }]}>Choose up to three. This helps readers find the story.</Text>
      {groups.map(([group, grouped]) => (
        <View key={group} style={styles.group}>
          <Text style={[styles.groupTitle, { color: e.muted }]}>{group}</Text>
          <View style={styles.chips}>
            {grouped.map((topicTag) => {
              const selected = value.includes(topicTag.id);
              const disabled = !selected && value.length >= max;
              return (
                <Pressable
                  key={topicTag.id}
                  accessibilityRole="button"
                  accessibilityLabel={`Topic tag ${topicTag.label}`}
                  accessibilityState={{ selected, disabled }}
                  disabled={disabled}
                  onPress={() => toggle(topicTag.id)}
                  style={[
                    styles.chip,
                    {
                      backgroundColor: selected ? e.lime : "transparent",
                      borderColor: selected ? e.lime : e.border,
                      opacity: disabled ? 0.45 : 1,
                    },
                  ]}
                >
                  <Text style={[styles.chipText, { color: selected ? e.onLime : e.secondary }]}>{topicTag.label}</Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      ))}
      {error ? <Text style={[styles.help, { color: e.coral }]}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  picker: { marginTop: 18, gap: 10 },
  heading: { flexDirection: "row", justifyContent: "space-between", alignItems: "baseline" },
  title: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 15 },
  count: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 0.5 },
  help: { fontFamily: EditorialFont.sans, fontSize: 11 },
  group: { gap: 7, marginTop: 4 },
  groupTitle: { fontFamily: EditorialFont.mono, fontSize: 9, letterSpacing: 0.7, textTransform: "uppercase" },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: 7 },
  chip: { borderWidth: 1, borderRadius: 8, paddingHorizontal: 11, paddingVertical: 6 },
  chipText: { fontFamily: EditorialFont.mono, fontSize: 10 },
});
