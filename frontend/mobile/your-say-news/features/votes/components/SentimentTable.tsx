import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment } from "../types";

/**
 * The "Table" view: a ranked row per group — name, agree (teal), disagree (coral), total, and a
 * mini agree/disagree lean bar. Groups arrive largest-total-first from the backend, so the table
 * reads top-down by size. Dense and scannable; counts only, never a voter.
 */
export function SentimentTable({ buckets }: { buckets: BucketSentiment[] }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View>
      <View style={styles.headRow}>
        <Text style={[styles.name, styles.h, { color: e.muted }]}>Group</Text>
        <Text style={[styles.num, styles.h, { color: e.muted }]}>Agree</Text>
        <Text style={[styles.num, styles.h, { color: e.muted }]}>Disagree</Text>
        <Text style={[styles.num, styles.h, { color: e.muted }]}>Total</Text>
        <Text style={[styles.lean, styles.h, { color: e.muted }]}>Lean</Text>
      </View>
      {buckets.map((b) => (
        <View key={b.bucket} style={[styles.row, { borderTopColor: e.border }]}>
          <Text style={[styles.name, styles.cellName, { color: e.ink }]} numberOfLines={1}>
            {prettifyBucket(b.bucket)}
          </Text>
          <Text style={[styles.num, styles.cellNum, { color: e.teal }]}>{b.yesCount}</Text>
          <Text style={[styles.num, styles.cellNum, { color: e.coral }]}>{b.noCount}</Text>
          <Text style={[styles.num, styles.cellTotal, { color: e.ink }]}>{b.total}</Text>
          <View style={styles.lean}>
            <View style={[styles.minibar, { backgroundColor: e.track }]}>
              <View style={{ flexGrow: b.yesCount, backgroundColor: e.voteAgreeFill }} />
              <View style={{ flexGrow: b.noCount, backgroundColor: e.coral }} />
            </View>
          </View>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  headRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingBottom: 10,
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 11,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  h: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.8,
    textTransform: "uppercase",
  },
  name: {
    flex: 1,
    textAlign: "left",
  },
  num: {
    width: 52,
    textAlign: "right",
  },
  cellName: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 13.5,
  },
  cellNum: {
    fontFamily: EditorialFont.mono,
    fontSize: 12.5,
  },
  cellTotal: {
    fontFamily: EditorialFont.monoSemiBold,
    fontWeight: "600",
    fontSize: 12.5,
  },
  lean: {
    width: 72,
    paddingLeft: 14,
  },
  minibar: {
    flexDirection: "row",
    height: 8,
    borderRadius: 4,
    overflow: "hidden",
    gap: 1,
  },
});
