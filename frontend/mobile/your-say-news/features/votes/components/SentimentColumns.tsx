import React, { useMemo } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment, ResultVoteOption } from "../types";
import { optionColor } from "./result-option-style";
const MAX_HEIGHT = 195;
export function SentimentColumns({ buckets, options }: { buckets: BucketSentiment[]; options: ResultVoteOption[] }) {
  const { isDark } = useTheme(); const e = getEditorial(isDark);
  const max = useMemo(() => buckets.reduce((m, b) => Math.max(m, b.total), 0), [buckets]);
  return <ScrollView horizontal contentContainerStyle={styles.chart}>{buckets.map((bucket) =>
    <View key={bucket.bucket} style={styles.column}><Text style={[styles.total, { color: e.ink }]}>{bucket.total}</Text>
      <View style={[styles.stack, { height: max ? Math.max(4, bucket.total / max * MAX_HEIGHT) : 4, backgroundColor: e.track }]}>
        {options.map((option, index) => <View key={option.id} style={{ flexGrow: bucket.choices.find((c) => c.optionId === option.id)?.count ?? 0,
          backgroundColor: optionColor(option, index, e) }} />)}
      </View><Text style={[styles.label, { color: e.secondary }]}>{prettifyBucket(bucket.bucket)}</Text></View>)}</ScrollView>;
}
const styles = StyleSheet.create({
  chart: { alignItems: "flex-end", gap: 12, minHeight: MAX_HEIGHT + 45 }, column: { alignItems: "center", justifyContent: "flex-end" },
  total: { fontFamily: EditorialFont.mono, fontSize: 13, marginBottom: 7 }, stack: { flexDirection: "column-reverse", width: 44, borderRadius: 5, overflow: "hidden" },
  label: { fontFamily: EditorialFont.mono, fontSize: 10.5, marginTop: 8, width: 70, textAlign: "center" },
});
