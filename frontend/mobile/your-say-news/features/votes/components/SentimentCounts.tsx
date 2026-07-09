import React, { useMemo } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment } from "../types";

/** Tallest a bar can draw, in px. Every bar scales against the largest single count on screen. */
const BAR_MAX_HEIGHT = 180;

/**
 * The "Counts" view: for each group, side-by-side agree (teal) and disagree (coral) columns whose
 * heights are the raw vote counts, scaled so the largest single count fills {@link BAR_MAX_HEIGHT}.
 * The count sits above each bar, the group name below. Scrolls horizontally when an axis has more
 * groups than fit. Counts only — a bar never names a voter.
 */
export function SentimentCounts({ buckets }: { buckets: BucketSentiment[] }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  // Scale every bar against the single largest count anywhere in the chart, so agree and disagree
  // share one axis and heights are comparable across groups.
  const maxCount = useMemo(
    () => buckets.reduce((m, b) => Math.max(m, b.yesCount, b.noCount), 0),
    [buckets]
  );
  const barHeight = (count: number) =>
    maxCount > 0 ? Math.max(2, Math.round((count / maxCount) * BAR_MAX_HEIGHT)) : 2;

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chart}>
      {buckets.map((b) => (
        <View key={b.bucket} style={styles.group}>
          <View style={styles.pair}>
            <Column
              count={b.yesCount}
              height={barHeight(b.yesCount)}
              color={e.voteAgreeFill}
              countColor={e.teal}
            />
            <Column
              count={b.noCount}
              height={barHeight(b.noCount)}
              color={e.coral}
              countColor={e.coral}
            />
          </View>
          <Text style={[styles.label, { color: e.secondary }]} numberOfLines={2}>
            {prettifyBucket(b.bucket)}
          </Text>
        </View>
      ))}
    </ScrollView>
  );
}

function Column({
  count,
  height,
  color,
  countColor,
}: {
  count: number;
  height: number;
  color: string;
  countColor: string;
}) {
  return (
    <View style={styles.col}>
      <Text style={[styles.count, { color: countColor }]}>{count}</Text>
      <View style={[styles.bar, { height, backgroundColor: color }]} />
    </View>
  );
}

const styles = StyleSheet.create({
  chart: {
    alignItems: "flex-end",
    gap: 16,
    paddingTop: 4,
    paddingRight: 8,
  },
  group: {
    alignItems: "center",
  },
  pair: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 5,
    height: BAR_MAX_HEIGHT + 22,
  },
  col: {
    alignItems: "center",
    justifyContent: "flex-end",
  },
  count: {
    fontFamily: EditorialFont.monoSemiBold,
    fontWeight: "600",
    fontSize: 12,
    marginBottom: 5,
  },
  bar: {
    width: 22,
    borderTopLeftRadius: 4,
    borderTopRightRadius: 4,
  },
  label: {
    fontFamily: EditorialFont.mono,
    fontSize: 10.5,
    textAlign: "center",
    marginTop: 9,
    lineHeight: 13,
    width: 58,
  },
});
