import React from "react";
import { View, StyleSheet } from "react-native";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment, ResultVoteOption } from "../types";
import { SentimentBar } from "./SentimentBar";

/**
 * The "Bars" view: one horizontal share-that-agree bar per group (the original results layout),
 * groups largest-total-first. Each bar animates its agree fill on mount — see {@link SentimentBar}.
 */
export function SentimentBars({ buckets, options }: { buckets: BucketSentiment[]; options: ResultVoteOption[] }) {
  return (
    <View style={styles.bars}>
      {buckets.map((b) => (
        <SentimentBar key={b.bucket} label={prettifyBucket(b.bucket)} bucket={b} options={options} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  bars: {
    gap: 18,
  },
});
