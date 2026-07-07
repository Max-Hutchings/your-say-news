import React, { useMemo, useState } from "react";
import { View, Text, ScrollView, ActivityIndicator, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { Eyebrow } from "@/components/ui";
import { useSentiment } from "../hooks/use-sentiment";
import { SENTIMENT_AXES, prettifyBucket } from "../data/axes";
import type { BucketSentiment, SentimentBreakdown, SentimentErrorKind } from "../types";
import { SentimentBar } from "./SentimentBar";
import { AxisChip } from "./AxisChip";

/**
 * The results view for a post the caller has voted on: the overall agree/disagree split up top,
 * a characteristic-axis selector, and one animated bar per group in the chosen axis (largest group
 * first — the backend sorts). Explore "how do Left / 25–34 / UK voters feel about this?" — always
 * counts and percentages, never a named person.
 */
export function SentimentResults({ postId }: { postId: number }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [axis, setAxis] = useState(SENTIMENT_AXES[0].field);
  const { overall, breakdown, loading, error, retry } = useSentiment(postId, axis);

  const overallBar = useMemo(() => aggregateOverall(overall), [overall]);

  return (
    <ScrollView
      style={{ backgroundColor: e.bg }}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      <Eyebrow text="How everyone voted" />
      {overallBar ? (
        <SentimentBar label="Overall" bucket={overallBar} overall />
      ) : error ? (
        <ErrorRow kind={error} onRetry={retry} e={e} />
      ) : (
        <ActivityIndicator testID="overall-loading" color={e.teal} style={styles.pad} />
      )}

      <View style={styles.divider} />

      <Eyebrow text="Break it down by" />
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.chips}
        keyboardShouldPersistTaps="handled"
      >
        {SENTIMENT_AXES.map((a) => (
          <AxisChip
            key={a.field}
            label={a.label}
            selected={a.field === axis}
            onPress={() => setAxis(a.field)}
          />
        ))}
      </ScrollView>

      <View style={styles.bars}>{renderBreakdown({ breakdown, loading, error, retry, e })}</View>
    </ScrollView>
  );
}

function renderBreakdown({
  breakdown,
  loading,
  error,
  retry,
  e,
}: {
  breakdown: SentimentBreakdown | null;
  loading: boolean;
  error: SentimentErrorKind | null;
  retry: () => void;
  e: ReturnType<typeof getEditorial>;
}) {
  if (loading) {
    return <ActivityIndicator testID="breakdown-loading" color={e.teal} style={styles.pad} />;
  }
  if (error) {
    return <ErrorRow kind={error} onRetry={retry} e={e} />;
  }
  if (!breakdown || breakdown.buckets.length === 0) {
    return (
      <Text style={[styles.empty, { color: e.muted }]}>
        Not enough votes to break this down yet.
      </Text>
    );
  }
  return (
    <>
      {breakdown.buckets.map((b) => (
        <SentimentBar key={b.bucket} label={prettifyBucket(b.bucket)} bucket={b} />
      ))}
      {breakdown.suppressedBuckets > 0 && (
        <Text style={[styles.empty, { color: e.muted }]}>
          {breakdown.suppressedBuckets} small group
          {breakdown.suppressedBuckets === 1 ? "" : "s"} hidden to protect privacy.
        </Text>
      )}
    </>
  );
}

function ErrorRow({
  kind,
  onRetry,
  e,
}: {
  kind: SentimentErrorKind;
  onRetry: () => void;
  e: ReturnType<typeof getEditorial>;
}) {
  return (
    <View style={styles.errorRow}>
      <Text style={[styles.empty, { color: e.coral }]}>{messageFor(kind)}</Text>
      <Pressable accessibilityRole="button" onPress={onRetry} style={[styles.retry, { borderColor: e.border }]}>
        <Text style={[styles.retryText, { color: e.ink }]}>Try again</Text>
      </Pressable>
    </View>
  );
}

function messageFor(kind: SentimentErrorKind): string {
  if (kind === "notVoted") return "Vote first to see how others voted.";
  if (kind === "auth") return "Please sign in again to see results.";
  if (kind === "network") return "No connection. Tap to try again.";
  return "Couldn't load results.";
}

/**
 * Collapse the overall breakdown into a single agree/disagree bar. The OVERALL endpoint returns
 * one bucket, which we pass through so its percentages match the backend exactly; if it ever
 * returns several, we sum the counts and recompute.
 */
function aggregateOverall(overall: SentimentBreakdown | null): BucketSentiment | null {
  if (!overall || overall.buckets.length === 0) return null;
  if (overall.buckets.length === 1) return overall.buckets[0];
  const yesCount = overall.buckets.reduce((s, b) => s + b.yesCount, 0);
  const noCount = overall.buckets.reduce((s, b) => s + b.noCount, 0);
  const total = yesCount + noCount;
  return {
    bucket: "OVERALL",
    yesCount,
    noCount,
    total,
    yesPct: total ? Math.round((yesCount / total) * 1000) / 10 : 0,
    noPct: total ? Math.round((noCount / total) * 1000) / 10 : 0,
  };
}

const styles = StyleSheet.create({
  content: {
    padding: 20,
    gap: 12,
  },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: "transparent",
    marginVertical: 2,
  },
  chips: {
    paddingVertical: 2,
    paddingRight: 12,
  },
  bars: {
    gap: 18,
    marginTop: 4,
  },
  pad: {
    paddingVertical: 24,
  },
  empty: {
    fontFamily: EditorialFont.mono,
    fontSize: 12,
    paddingVertical: 8,
  },
  errorRow: {
    gap: 10,
    alignItems: "flex-start",
    paddingVertical: 8,
  },
  retry: {
    borderWidth: 1.5,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  retryText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 13,
  },
});
