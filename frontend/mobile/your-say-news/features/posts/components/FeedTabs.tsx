import React, { useMemo, useState } from "react";
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { useTopics, type Topic } from "@/features/topics";

type FeedTabsProps = {
  value: string | null;
  onChange: (topicId: string | null) => void;
};

/** Functional category strip. Four curated topics stay visible; More opens the full catalogue. */
export function FeedTabs({ value, onChange }: FeedTabsProps) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { topics } = useTopics();
  const [moreOpen, setMoreOpen] = useState(false);
  const selected = topics.find((topic) => topic.id === value);
  const visibleTopics = useMemo(() => {
    const first = topics.slice(0, 4);
    if (!selected || first.some((topic) => topic.id === selected.id)) return first;
    return [...first.slice(0, 3), selected];
  }, [selected, topics]);
  const groups = useMemo(() => groupTopics(topics), [topics]);

  const choose = (topicId: string | null) => {
    onChange(topicId);
    setMoreOpen(false);
  };

  return (
    <>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        <Tab label="For you" active={value === null} onPress={() => choose(null)} />
        {visibleTopics.map((topic) => (
          <Tab
            key={topic.id}
            label={topic.label}
            active={value === topic.id}
            onPress={() => choose(topic.id)}
          />
        ))}
        <Tab label="More ▾" active={false} onPress={() => setMoreOpen(true)} />
      </ScrollView>

      <Modal
        visible={moreOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setMoreOpen(false)}
      >
        <Pressable
          accessibilityLabel="Close topic list"
          style={[styles.backdrop, { backgroundColor: e.mediaScrim }]}
          onPress={() => setMoreOpen(false)}
        >
          <Pressable
            accessibilityRole="none"
            style={[styles.sheet, { backgroundColor: e.bg, borderColor: e.border }]}
            onPress={(event) => event.stopPropagation()}
          >
            <View style={[styles.sheetHeader, { borderBottomColor: e.ink }]}>
              <Text style={[styles.sheetEyebrow, { color: e.muted }]}>ALL TOPICS</Text>
              <Pressable accessibilityRole="button" onPress={() => setMoreOpen(false)}>
                <Text style={[styles.close, { color: e.ink }]}>Close</Text>
              </Pressable>
            </View>
            <ScrollView contentContainerStyle={styles.topicList}>
              {groups.map(([group, groupedTopics]) => (
                <View key={group} style={styles.group}>
                  <Text style={[styles.groupTitle, { color: e.muted }]}>{group}</Text>
                  <View style={styles.groupTopics}>
                    {groupedTopics.map((topic) => (
                      <Tab
                        key={topic.id}
                        label={topic.label}
                        active={value === topic.id}
                        onPress={() => choose(topic.id)}
                      />
                    ))}
                  </View>
                </View>
              ))}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
    </>
  );
}

function Tab({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
      testID={`feed-tab-${label}`}
      onPress={onPress}
      style={[
        styles.tab,
        active
          ? { backgroundColor: e.lime, borderColor: e.lime }
          : { backgroundColor: "transparent", borderColor: e.border },
      ]}
    >
      <Text style={[styles.label, { color: active ? e.onLime : e.secondary }]}>{label}</Text>
    </Pressable>
  );
}

function groupTopics(topics: Topic[]): [string, Topic[]][] {
  const groups = new Map<string, Topic[]>();
  topics.forEach((topic) => groups.set(topic.displayGroup, [...(groups.get(topic.displayGroup) ?? []), topic]));
  return [...groups.entries()];
}

const styles = StyleSheet.create({
  row: { paddingHorizontal: 22, gap: 8, alignItems: "center" },
  tab: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 8, borderWidth: 1 },
  label: { fontFamily: EditorialFont.mono, fontSize: 11 },
  backdrop: { flex: 1, justifyContent: "flex-end" },
  sheet: {
    maxHeight: "78%",
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderWidth: 1,
    paddingHorizontal: 20,
    paddingBottom: 28,
  },
  sheetHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 18,
    borderBottomWidth: 2,
  },
  sheetEyebrow: { fontFamily: EditorialFont.monoSemiBold, fontSize: 11, letterSpacing: 1.4 },
  close: { fontFamily: EditorialFont.sansBold, fontSize: 14 },
  topicList: { paddingTop: 18, gap: 22 },
  group: { gap: 10 },
  groupTitle: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 0.9, textTransform: "uppercase" },
  groupTopics: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
});
