import React from "react";
import { View, StyleSheet } from "react-native";
import { prettifyBucket } from "../data/axes";
import type { BucketSentiment } from "../types";
import { SentimentBar } from "./SentimentBar";

/**
 * The "Bars" view: one horizontal share-that-agree bar per group (the original results layout),
 * groups largest-total-first. Each bar animates its agree fill on mount — see {@link SentimentBar}.
 */
export function SentimentBars({ buckets }: { buckets: BucketSentiment[] }) {
  return (
    <View style={styles.bars}>
      {buckets.map((b) => (
        <SentimentBar key={b.bucket} label={prettifyBucket(b.bucket)} bucket={b} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  bars: {
    gap: 18,
  },
});
