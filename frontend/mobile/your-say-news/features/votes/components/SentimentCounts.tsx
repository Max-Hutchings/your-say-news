import React, { useMemo } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment, ResultVoteOption } from "../types";
import { optionColor } from "./result-option-style";

const BAR_MAX_HEIGHT = 180;
export function SentimentCounts({ buckets, options }: { buckets: BucketSentiment[]; options: ResultVoteOption[] }) {
  const { isDark } = useTheme(); const e = getEditorial(isDark);
  const max = useMemo(() => buckets.flatMap((b) => b.choices).reduce((m, c) => Math.max(m, c.count), 0), [buckets]);
  return <ScrollView horizontal contentContainerStyle={styles.chart}>
    {buckets.map((bucket) => <View key={bucket.bucket} style={styles.group}>
      <View style={styles.columns}>{options.map((option, index) => {
        const count = bucket.choices.find((choice) => choice.optionId === option.id)?.count ?? 0;
        const height = max ? Math.max(2, Math.round(count / max * BAR_MAX_HEIGHT)) : 2;
        return <View key={option.id} style={styles.column}>
          <Text style={[styles.count, { color: optionColor(option, index, e) }]}>{count}</Text>
          <View style={[styles.bar, { height, backgroundColor: optionColor(option, index, e) }]} />
        </View>;
      })}</View>
      <Text style={[styles.label, { color: e.secondary }]}>{prettifyBucket(bucket.bucket)}</Text>
    </View>)}
  </ScrollView>;
}
const styles = StyleSheet.create({
  chart: { alignItems: "flex-end", gap: 16, paddingTop: 4, paddingRight: 8 }, group: { alignItems: "center" },
  columns: { flexDirection: "row", alignItems: "flex-end", gap: 4, height: BAR_MAX_HEIGHT + 22 }, column: { alignItems: "center", justifyContent: "flex-end" },
  count: { fontFamily: EditorialFont.monoSemiBold, fontSize: 11, marginBottom: 4 }, bar: { width: 18, borderTopLeftRadius: 4, borderTopRightRadius: 4 },
  label: { fontFamily: EditorialFont.mono, fontSize: 10.5, textAlign: "center", marginTop: 9, width: 72 },
});
