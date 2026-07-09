import React, { useMemo } from "react";
import { View, Text, ScrollView, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment } from "../types";

/** Tallest a column can draw, in px. Column height encodes a group's TOTAL vote count. */
const STACK_MAX_HEIGHT = 195;

/**
 * The "Columns" view: one stacked column per group, its height proportional to the group's TOTAL
 * votes (so bigger groups read as taller), split into an agree (teal, bottom) and disagree (coral,
 * top) segment by their share. Total sits above, group name and agree-% below. Scrolls
 * horizontally when groups overflow. Counts + percentages only.
 */
export function SentimentColumns({ buckets }: { buckets: BucketSentiment[] }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  const maxTotal = useMemo(() => buckets.reduce((m, b) => Math.max(m, b.total), 0), [buckets]);
  const stackHeight = (total: number) =>
    maxTotal > 0 ? Math.max(4, Math.round((total / maxTotal) * STACK_MAX_HEIGHT)) : 4;

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chart}>
      {buckets.map((b) => (
        <View key={b.bucket} style={styles.col}>
          <Text style={[styles.total, { color: e.ink }]}>{b.total}</Text>
          {/* column-reverse so the agree segment (first child) anchors to the bottom. */}
          <View style={[styles.stack, { height: stackHeight(b.total), backgroundColor: e.track }]}>
            <View style={{ flexGrow: b.yesCount, backgroundColor: e.voteAgreeFill }} />
            <View style={{ flexGrow: b.noCount, backgroundColor: e.coral }} />
          </View>
          <Text style={[styles.label, { color: e.secondary }]} numberOfLines={2}>
            {prettifyBucket(b.bucket)}
          </Text>
          <Text style={[styles.pct, { color: e.teal }]}>{formatPct(b.yesPct)}%</Text>
        </View>
      ))}
    </ScrollView>
  );
}

/** One decimal, but no trailing ".0" — matches SentimentBar's percentage formatting. */
function formatPct(pct: number): string {
  return Number.isInteger(pct) ? String(pct) : pct.toFixed(1);
}

const styles = StyleSheet.create({
  chart: {
    alignItems: "flex-end",
    gap: 12,
    paddingTop: 4,
    paddingRight: 8,
    minHeight: STACK_MAX_HEIGHT + 60,
  },
  col: {
    alignItems: "center",
    justifyContent: "flex-end",
  },
  total: {
    fontFamily: EditorialFont.mono,
    fontSize: 13,
    marginBottom: 7,
  },
  stack: {
    flexDirection: "column-reverse",
    width: 44,
    borderRadius: 5,
    overflow: "hidden",
    gap: 2,
  },
  label: {
    fontFamily: EditorialFont.mono,
    fontSize: 10.5,
    textAlign: "center",
    marginTop: 9,
    lineHeight: 13,
    width: 58,
  },
  pct: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 11,
    marginTop: 3,
  },
});
