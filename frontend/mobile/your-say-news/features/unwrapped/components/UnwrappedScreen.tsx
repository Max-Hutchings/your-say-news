import React, { useState } from "react";
import {
  ActivityIndicator,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { SentimentResults } from "@/features/votes";
import { useUnwrapped } from "../hooks/use-unwrapped";
import type {
  UnwrappedArgumentPage,
  UnwrappedSource,
  UnwrappedStory,
} from "../types";

export function UnwrappedScreen({ postId }: { postId: number }) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { data, loading, submitting, error, refresh, followUp } = useUnwrapped(postId);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [showResults, setShowResults] = useState(false);

  if (showResults) {
    return (
      <View style={[styles.root, { backgroundColor: e.bg }]}>
        <View style={[styles.resultsHeader, { borderBottomColor: e.border }]}>
          <Pressable accessibilityRole="button" accessibilityLabel="Back to feed" onPress={() => router.back()}>
            <Ionicons name="close" size={24} color={e.ink} />
          </Pressable>
          <Text style={[styles.resultsTitle, { color: e.ink }]}>How people voted</Text>
        </View>
        <SentimentResults postId={postId} />
      </View>
    );
  }

  if (loading) {
    return <StateScreen title="Opening the context…" action={null} spinner />;
  }
  if (!data) {
    return <StateScreen title="The context could not be loaded."
      action={{ label: "Try again", run: () => void refresh() }} />;
  }
  if (!data.story) {
    const building = data.state === "BUILDING";
    return <StateScreen title={data.notice}
      action={building
        ? { label: "Check again", run: () => void refresh() }
        : { label: "See factual results", run: () => setShowResults(true) }}
      secondaryAction={building
        ? { label: "Continue to factual results", run: () => setShowResults(true) }
        : null} />;
  }

  const story = data.story;
  const finalPage = story.argumentPages.length;
  const totalPages = finalPage + 1;
  const currentArgument = page < finalPage ? story.argumentPages[page] : null;

  const continueFromFinal = async () => {
    if (data.existingFollowUpOptionId != null) {
      setShowResults(true);
      return;
    }
    if (selected == null) return;
    if (await followUp(story.storyId, selected)) setShowResults(true);
  };

  return (
    <View style={[styles.root, { backgroundColor: e.bg }]}>
      <View style={[styles.topBar, { borderBottomColor: e.border }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Close Unwrapped" onPress={() => router.back()}>
          <Ionicons name="close" size={23} color={e.ink} />
        </Pressable>
        <View style={styles.progressWrap}>
          <Text style={[styles.progressLabel, { color: e.muted }]}>
            POST UNWRAPPED · {page + 1} OF {totalPages}
          </Text>
          <View style={[styles.progressTrack, { backgroundColor: e.track }]}>
            <View style={[styles.progressFill, {
              backgroundColor: e.lime,
              width: `${((page + 1) / totalPages) * 100}%`,
            }]} />
          </View>
        </View>
      </View>

      {currentArgument
        ? <ArgumentPage page={currentArgument} story={story} />
        : <ReconsiderationPage story={story} original={data.originalOptionId}
            existing={data.existingFollowUpOptionId} selected={selected} onSelect={setSelected} />}

      {error && (
        <Text accessibilityRole="alert" style={[styles.inlineError, { color: e.coral }]}>
          Your follow-up was not saved. Check your connection and try again.
        </Text>
      )}

      <View style={[styles.navigation, { borderTopColor: e.border, backgroundColor: e.surface }]}>
        <Pressable accessibilityRole="button" disabled={page === 0}
          onPress={() => setPage((value) => value - 1)}
          style={[styles.secondaryButton, { borderColor: e.border, opacity: page === 0 ? 0.35 : 1 }]}>
          <Text style={[styles.secondaryButtonText, { color: e.ink }]}>Back</Text>
        </Pressable>
        <Pressable accessibilityRole="button"
          accessibilityLabel={page === finalPage ? "See live results" : "Next argument"}
          disabled={submitting || (page === finalPage && selected == null
            && data.existingFollowUpOptionId == null)}
          onPress={() => page === finalPage
            ? void continueFromFinal()
            : setPage((value) => value + 1)}
          style={[styles.primaryButton, { backgroundColor: e.lime,
            opacity: submitting || (page === finalPage && selected == null
              && data.existingFollowUpOptionId == null) ? 0.45 : 1 }]}>
          {submitting
            ? <ActivityIndicator color={e.onLime} />
            : <Text style={[styles.primaryButtonText, { color: e.onLime }]}>
                {page === finalPage ? "See live results" : "Next argument"}
              </Text>}
        </Pressable>
      </View>
    </View>
  );
}

function ArgumentPage({ page, story }: {
  page: UnwrappedArgumentPage;
  story: UnwrappedStory;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const option = story.reconsiderationOptions.find((value) => value.id === page.optionId);
  const sourceIds = page.sources.map((source) => source.id);

  return (
    <ScrollView contentContainerStyle={styles.page} accessibilityRole="summary">
      <Text style={[styles.optionLabel, { color: e.teal }]}>THE CASE FOR</Text>
      <Text style={[styles.optionName, { color: e.secondary }]}>{option?.label}</Text>
      <Text style={[styles.headline, { color: e.ink }]}>{page.headline}</Text>

      <View style={styles.article}>
        {page.paragraphs.map((paragraph, index) => (
          <View key={`${page.optionId}-${index}`} style={styles.paragraph}>
            <Text style={[styles.articleText, { color: e.ink }]}>{paragraph.text}</Text>
            <Text style={[styles.citation, { color: e.teal }]}>
              {paragraph.sourceIds.map((id) => `[${sourceIds.indexOf(id) + 1}]`).join(" ")}
            </Text>
          </View>
        ))}
      </View>

      <View style={[styles.sources, { borderTopColor: e.border }]}>
        <Text style={[styles.sectionLabel, { color: e.muted }]}>DATA SOURCES</Text>
        {page.sources.map((source, index) => (
          <SourceRow key={source.id} index={index + 1} source={source} />
        ))}
      </View>
    </ScrollView>
  );
}

function ReconsiderationPage({ story, original, existing, selected, onSelect }: {
  story: UnwrappedStory;
  original: number;
  existing: number | null;
  selected: number | null;
  onSelect: (optionId: number) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const chosen = existing ?? selected;
  return (
    <ScrollView contentContainerStyle={styles.page}>
      <Text style={[styles.optionLabel, { color: e.coral }]}>ONE MORE VOTE</Text>
      <Text style={[styles.headline, { color: e.ink }]}>{story.reconsiderationQuestion}</Text>
      <Text style={[styles.body, { color: e.secondary }]}>
        Your first answer remains your official vote. This second answer only tells us whether the context mattered.
      </Text>
      <View style={styles.optionStack}>
        {story.reconsiderationOptions.map((option) => {
          const active = chosen === option.id;
          return (
            <Pressable key={option.id} accessibilityRole="radio"
              accessibilityState={{ checked: active, disabled: existing != null }}
              accessibilityLabel={option.label} disabled={existing != null}
              onPress={() => onSelect(option.id)}
              style={[styles.optionCard, {
                borderColor: active ? e.teal : e.border,
                backgroundColor: active ? e.voteAgreeBg : e.surface,
              }]}>
              <Ionicons name={active ? "radio-button-on" : "radio-button-off"}
                size={21} color={active ? e.teal : e.muted} />
              <View style={styles.optionText}>
                <Text style={[styles.optionCardLabel, { color: e.ink }]}>{option.label}</Text>
                {option.id === original && (
                  <Text style={[styles.original, { color: e.muted }]}>YOUR ORIGINAL VOTE</Text>
                )}
              </View>
            </Pressable>
          );
        })}
      </View>
      {existing != null && (
        <Text style={[styles.caveat, { color: e.muted }]}>
          You already answered this follow-up. You can revisit the context, but your response is recorded once.
        </Text>
      )}
    </ScrollView>
  );
}

function SourceRow({ index, source }: { index: number; source: UnwrappedSource }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return (
    <Pressable accessibilityRole="link" onPress={() => void Linking.openURL(source.url)}
      style={styles.sourceRow}>
      <Text style={[styles.sourceNumber, { color: e.teal }]}>{index.toString().padStart(2, "0")}</Text>
      <View style={styles.sourceCopy}>
        <Text style={[styles.sourceTitle, { color: e.ink }]}>{source.title}</Text>
        <Text style={[styles.sourceMeta, { color: e.muted }]}>
          {source.publisher} · {source.classification.replace("_", " ")}
        </Text>
      </View>
      <Ionicons name="open-outline" size={16} color={e.muted} />
    </Pressable>
  );
}

function StateScreen({ title, action, secondaryAction = null, spinner = false }: {
  title: string;
  action: { label: string; run: () => void } | null;
  secondaryAction?: { label: string; run: () => void } | null;
  spinner?: boolean;
}) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return (
    <View style={[styles.state, { backgroundColor: e.bg }]}>
      <Text style={[styles.optionLabel, { color: e.teal }]}>POST UNWRAPPED</Text>
      <Text style={[styles.headline, { color: e.ink }]}>{title}</Text>
      {spinner && <ActivityIndicator color={e.teal} />}
      {action && <Pressable accessibilityRole="button" onPress={action.run}
        style={[styles.primaryButton, { backgroundColor: e.lime }]}>
        <Text style={[styles.primaryButtonText, { color: e.onLime }]}>{action.label}</Text>
      </Pressable>}
      {secondaryAction && <Pressable accessibilityRole="button" onPress={secondaryAction.run}
        style={[styles.secondaryButton, { borderColor: e.border }]}>
        <Text style={[styles.secondaryButtonText, { color: e.ink }]}>
          {secondaryAction.label}
        </Text>
      </Pressable>}
      <Pressable accessibilityRole="button" onPress={() => router.back()}>
        <Text style={[styles.secondaryButtonText, { color: e.secondary }]}>Back to the feed</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  topBar: { minHeight: 74, paddingTop: 18, paddingHorizontal: 20, paddingBottom: 12,
    flexDirection: "row", alignItems: "center", gap: 18, borderBottomWidth: 1 },
  progressWrap: { flex: 1, gap: 8 },
  progressLabel: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10, letterSpacing: 1.1 },
  progressTrack: { height: 3, borderRadius: 2, overflow: "hidden" },
  progressFill: { height: 3 },
  page: { padding: 24, paddingBottom: 42, gap: 16 },
  optionLabel: { fontFamily: EditorialFont.monoSemiBold, fontSize: 11, letterSpacing: 1.4 },
  optionName: { fontFamily: EditorialFont.sansSemiBold, fontSize: 14, marginTop: -10 },
  headline: { fontFamily: EditorialFont.serif, fontSize: 36, lineHeight: 40 },
  sectionLabel: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10, letterSpacing: 1.2 },
  body: { fontFamily: EditorialFont.sans, fontSize: 16, lineHeight: 24 },
  article: { gap: 16, marginVertical: 4 },
  paragraph: { gap: 5 },
  articleText: { fontFamily: EditorialFont.serifRegular, fontSize: 20, lineHeight: 29 },
  citation: { fontFamily: EditorialFont.mono, fontSize: 10 },
  caveat: { fontFamily: EditorialFont.sans, fontSize: 12, lineHeight: 18 },
  sources: { borderTopWidth: 1, paddingTop: 18, gap: 12 },
  sourceRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  sourceNumber: { width: 24, fontFamily: EditorialFont.monoSemiBold, fontSize: 11 },
  sourceCopy: { flex: 1, gap: 2 },
  sourceTitle: { fontFamily: EditorialFont.sansMedium, fontSize: 13 },
  sourceMeta: { fontFamily: EditorialFont.mono, fontSize: 9, textTransform: "uppercase" },
  navigation: { borderTopWidth: 1, padding: 16, flexDirection: "row", gap: 12 },
  primaryButton: { flex: 1, minHeight: 52, borderRadius: 14, paddingHorizontal: 18,
    alignItems: "center", justifyContent: "center" },
  primaryButtonText: { fontFamily: EditorialFont.sansBold, fontSize: 15 },
  secondaryButton: { minWidth: 90, minHeight: 52, borderRadius: 14, borderWidth: 1,
    alignItems: "center", justifyContent: "center" },
  secondaryButtonText: { fontFamily: EditorialFont.sansSemiBold, fontSize: 14 },
  optionStack: { gap: 10, marginTop: 4 },
  optionCard: { minHeight: 68, borderRadius: 14, borderWidth: 1.5, padding: 14,
    flexDirection: "row", alignItems: "center", gap: 12 },
  optionText: { flex: 1, gap: 3 },
  optionCardLabel: { fontFamily: EditorialFont.sansSemiBold, fontSize: 16 },
  original: { fontFamily: EditorialFont.monoSemiBold, fontSize: 9, letterSpacing: 0.8 },
  state: { flex: 1, padding: 28, alignItems: "flex-start", justifyContent: "center", gap: 24 },
  resultsHeader: { minHeight: 64, borderBottomWidth: 1, paddingHorizontal: 20,
    flexDirection: "row", alignItems: "center", gap: 18 },
  resultsTitle: { fontFamily: EditorialFont.serif, fontSize: 23 },
  inlineError: { paddingHorizontal: 20, paddingVertical: 8, fontFamily: EditorialFont.sansMedium,
    fontSize: 13, textAlign: "center" },
});
