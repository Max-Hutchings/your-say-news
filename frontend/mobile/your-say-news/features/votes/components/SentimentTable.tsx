import React from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment, ResultVoteOption } from "../types";
import { optionColor } from "./result-option-style";

export function SentimentTable({ buckets, options }: { buckets: BucketSentiment[]; options: ResultVoteOption[] }) {
  const { isDark } = useTheme(); const e = getEditorial(isDark);
  return <ScrollView horizontal><View>
    <View style={styles.row}><Text style={[styles.group, { color: e.muted }]}>Group</Text>
      {options.map((o) => <Text key={o.id} style={[styles.value, { color: e.muted }]}>{o.label}</Text>)}
      <Text style={[styles.value, { color: e.muted }]}>Total</Text></View>
    {buckets.map((bucket) => <View key={bucket.bucket} style={[styles.row, { borderTopColor: e.border }]}>
      <Text style={[styles.group, { color: e.ink }]}>{prettifyBucket(bucket.bucket)}</Text>
      {options.map((option, index) => <Text key={option.id} style={[styles.value, { color: optionColor(option, index, e) }]}>
        {bucket.choices.find((choice) => choice.optionId === option.id)?.count ?? 0}
      </Text>)}
      <Text style={[styles.value, { color: e.ink }]}>{bucket.total}</Text>
    </View>)}
  </View></ScrollView>;
}
const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", minHeight: 42, borderTopWidth: StyleSheet.hairlineWidth },
  group: { width: 110, fontFamily: EditorialFont.sansSemiBold, fontSize: 12 },
  value: { width: 110, paddingHorizontal: 5, fontFamily: EditorialFont.mono, fontSize: 11, textAlign: "right" },
});
