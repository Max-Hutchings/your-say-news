import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { BucketSentiment, ResultVoteOption } from "../types";
import { formatPct, optionColor } from "./result-option-style";

export function SentimentBar({ label, bucket, options, overall = false }: {
  label: string; bucket: BucketSentiment; options: ResultVoteOption[]; overall?: boolean;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return <View style={styles.wrap}>
    <Text style={[overall ? styles.overallLabel : styles.label, { color: e.ink }]}>{label}</Text>
    {options.map((option, index) => {
      const choice = bucket.choices.find((item) => item.optionId === option.id) ?? { optionId: option.id, count: 0, percentage: 0 };
      const color = optionColor(option, index, e);
      return <View key={option.id} style={styles.choice}>
        <View style={styles.row}>
          <Text style={[styles.choiceLabel, { color: e.ink }]}>{option.label}</Text>
          <Text style={[styles.pct, { color }]}>{formatPct(choice.percentage)}%</Text>
        </View>
        <View style={[styles.track, { backgroundColor: e.track }]}>
          <View style={[styles.fill, { backgroundColor: color, width: `${Math.max(0, Math.min(100, choice.percentage))}%` }]} />
        </View>
        <Text style={[styles.counts, { color: e.muted }]}>{choice.count} of {bucket.total} votes</Text>
      </View>;
    })}
  </View>;
}

const styles = StyleSheet.create({
  wrap: { gap: 10 }, choice: { gap: 5 }, row: { flexDirection: "row", alignItems: "center", gap: 8 },
  label: { fontFamily: EditorialFont.sansSemiBold, fontWeight: "600", fontSize: 15 },
  overallLabel: { fontFamily: EditorialFont.serif, fontSize: 20 },
  choiceLabel: { flex: 1, fontFamily: EditorialFont.sansSemiBold, fontSize: 14 },
  pct: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 14 },
  track: { height: 12, borderRadius: 6, overflow: "hidden" }, fill: { height: "100%", borderRadius: 6 },
  counts: { fontFamily: EditorialFont.mono, fontSize: 11 },
});
