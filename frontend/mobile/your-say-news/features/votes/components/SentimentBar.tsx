import React, { useEffect, useMemo } from "react";
import { View, Text, Animated, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { BucketSentiment } from "../types";

/**
 * One horizontal agree/disagree bar for a single group. The teal agree segment animates its width
 * from 0 to `yesPct` on mount and whenever the group's numbers change (axis switch), over the coral
 * disagree track — the same teal/coral pairing as the feed's vote row.
 *
 * Shows the agree percentage and the raw counts. Counts + percentages only — a bar never names a
 * person. `label` is the already-prettified group name; `overall` gives the summary row a larger,
 * emphasised treatment.
 */
export function SentimentBar({
  label,
  bucket,
  overall = false,
}: {
  label: string;
  bucket: BucketSentiment;
  overall?: boolean;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  // Animate the agree fill's width to yesPct%. Width uses a percentage, so this can't run on the
  // native driver. Re-runs when yesPct changes so switching axis re-animates to the new split.
  const grow = useMemo(() => new Animated.Value(0), []);
  useEffect(() => {
    grow.setValue(0);
    Animated.timing(grow, {
      toValue: bucket.yesPct,
      duration: 520,
      useNativeDriver: false,
    }).start();
  }, [grow, bucket.yesPct]);

  const width = grow.interpolate({
    inputRange: [0, 100],
    outputRange: ["0%", "100%"],
    extrapolate: "clamp",
  });

  return (
    <View style={styles.wrap}>
      <View style={styles.labelRow}>
        <Text
          style={[overall ? styles.overallLabel : styles.label, { color: e.ink }]}
          numberOfLines={1}
        >
          {label}
        </Text>
        <View style={styles.spacer} />
        <Text style={[overall ? styles.overallPct : styles.pct, { color: e.teal }]}>
          {formatPct(bucket.yesPct)}%
        </Text>
      </View>

      <View style={[styles.track, overall && styles.trackTall, { backgroundColor: e.coral }]}>
        <Animated.View style={[styles.fill, { width, backgroundColor: e.voteAgreeFill }]} />
      </View>

      <Text style={[styles.counts, { color: e.muted }]}>
        {bucket.yesCount} agree · {bucket.noCount} disagree · {bucket.total} voted
      </Text>
    </View>
  );
}

/** One decimal, but no trailing ".0" — 80 → "80", 66.7 → "66.7". */
function formatPct(pct: number): string {
  return Number.isInteger(pct) ? String(pct) : pct.toFixed(1);
}

const styles = StyleSheet.create({
  wrap: {
    gap: 6,
  },
  labelRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  spacer: {
    flex: 1,
  },
  label: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 15,
  },
  overallLabel: {
    fontFamily: EditorialFont.serif,
    fontSize: 20,
  },
  pct: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 15,
  },
  overallPct: {
    fontFamily: EditorialFont.serif,
    fontSize: 24,
  },
  track: {
    height: 12,
    borderRadius: 6,
    overflow: "hidden",
  },
  trackTall: {
    height: 18,
    borderRadius: 9,
  },
  fill: {
    height: "100%",
    borderRadius: 6,
  },
  counts: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
  },
});
