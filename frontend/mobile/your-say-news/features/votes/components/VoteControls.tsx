import React, { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { VoteOption, VotingType } from "@/features/posts";
import { useVote } from "../hooks/use-vote";
import type { VoteErrorKind } from "../types";
import { MultipleChoiceVoteSheet } from "./MultipleChoiceVoteSheet";
import { SentimentResultsSheet } from "./SentimentResultsSheet";

export function VoteControls({ postId, votingType, options, supportQuestion, onNextPost }: {
  postId: number;
  votingType: VotingType;
  options: VoteOption[];
  supportQuestion: string;
  onNextPost?: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { loading, myVote, locked, submitting, error, vote } = useVote(postId);
  const [choiceOpen, setChoiceOpen] = useState(false);
  const [resultsOpen, setResultsOpen] = useState(false);
  const selected = options.find((option) => option.id === myVote);

  const record = async (optionId: number) => {
    if (await vote(optionId)) {
      setChoiceOpen(false);
      setResultsOpen(true);
    }
  };

  return (
    <View>
      {votingType === "MULTIPLE_CHOICE" ? (
        <Pressable
          testID="vote-multiple-choice"
          accessibilityRole="button"
          accessibilityLabel={locked ? selected ? `You chose ${selected.label}` : "Vote already recorded" : "Have your say"}
          accessibilityState={{ disabled: loading || submitting || locked }}
          disabled={loading || submitting || locked}
          onPress={() => setChoiceOpen(true)}
          style={[styles.multipleButton, { backgroundColor: e.voteAgreeFill, opacity: locked ? 0.72 : 1 }]}
        >
          {submitting && <ActivityIndicator size="small" color={e.voteAgreeOnFill} />}
          <Text style={[styles.multipleText, { color: e.voteAgreeOnFill }]}>
            {locked ? selected ? `You chose — ${selected.label}` : "Vote already recorded" : "Have your say..."}
          </Text>
        </Pressable>
      ) : (
        <BinaryControls
          options={options}
          myVote={myVote}
          disabled={loading || submitting || locked}
          submitting={submitting}
          onVote={(optionId) => void record(optionId)}
          e={e}
        />
      )}

      {captionFor({ locked, selectedLabel: selected?.label, submitting, error, votingType }) ? (
        <Text testID="vote-status" style={[styles.status, { color: error ? e.coral : e.muted }]} numberOfLines={2}>
          {captionFor({ locked, selectedLabel: selected?.label, submitting, error, votingType })}
        </Text>
      ) : null}

      {locked && (
        <Pressable testID="see-results" accessibilityRole="button" onPress={() => setResultsOpen(true)}
          style={[styles.resultsBtn, { borderColor: e.border, backgroundColor: e.surface }]}>
          <Ionicons name="stats-chart" size={16} color={e.teal} />
          <Text style={[styles.resultsText, { color: e.ink }]}>See how others voted</Text>
        </Pressable>
      )}

      <MultipleChoiceVoteSheet visible={choiceOpen} supportQuestion={supportQuestion}
        options={options} submitting={submitting} error={error}
        onSubmit={(optionId) => void record(optionId)} onClose={() => setChoiceOpen(false)} />
      <SentimentResultsSheet postId={postId} visible={resultsOpen} onClose={() => setResultsOpen(false)}
        onNextPost={onNextPost} />
    </View>
  );
}

function BinaryControls({ options, myVote, disabled, submitting, onVote, e }: {
  options: VoteOption[];
  myVote: number | null;
  disabled: boolean;
  submitting: boolean;
  onVote: (optionId: number) => void;
  e: ReturnType<typeof getEditorial>;
}) {
  const agree = options.find((option) => option.semanticKey === "AGREE");
  const disagree = options.find((option) => option.semanticKey === "DISAGREE");
  const agreeChosen = myVote === agree?.id;
  const disagreeChosen = myVote === disagree?.id;
  return (
    <View style={styles.voteRow}>
      <Pressable testID="vote-agree" accessibilityRole="button" accessibilityLabel="Agree"
        accessibilityState={{ disabled: disabled || !agree, selected: agreeChosen }} disabled={disabled || !agree}
        onPress={() => agree && onVote(agree.id)}
        style={[styles.voteBtn, { backgroundColor: e.voteAgreeFill, borderColor: e.voteAgreeFill, opacity: myVote && !agreeChosen ? 0.55 : 1 }]}>
        <Ionicons name={agreeChosen ? "checkmark-circle" : "thumbs-up"} size={19} color={e.voteAgreeOnFill} />
        <Text style={[styles.voteBtnText, { color: e.voteAgreeOnFill }]}>Agree</Text>
      </Pressable>
      <Pressable testID="vote-disagree" accessibilityRole="button" accessibilityLabel="Disagree"
        accessibilityState={{ disabled: disabled || !disagree, selected: disagreeChosen }} disabled={disabled || !disagree}
        onPress={() => disagree && onVote(disagree.id)}
        style={[styles.voteBtn, { backgroundColor: disagreeChosen ? e.coral : e.voteDisagreeFill,
          borderColor: e.coral, opacity: myVote && !disagreeChosen ? 0.55 : 1 }]}>
        {submitting ? <ActivityIndicator testID="vote-submitting" size="small" color={e.coral} />
          : <Ionicons name={disagreeChosen ? "checkmark-circle" : "thumbs-down"} size={19}
              color={disagreeChosen ? "#FFFFFF" : e.coral} />}
        <Text style={[styles.voteBtnText, { color: disagreeChosen ? "#FFFFFF" : e.coral }]}>Disagree</Text>
      </Pressable>
    </View>
  );
}

function captionFor({ locked, selectedLabel, submitting, error, votingType }: {
  locked: boolean; selectedLabel?: string; submitting: boolean; error: VoteErrorKind | null; votingType: VotingType;
}) {
  if (submitting) return "Recording your vote…";
  if (error === "auth") return "Please sign in again to vote.";
  if (error === "network") return "No connection. Tap to try again.";
  if (error === "unknown") return "Couldn't record your vote. Tap again.";
  if (locked && votingType === "BINARY") return selectedLabel ? `You voted — ${selectedLabel}` : "Vote already recorded";
  return "";
}

const styles = StyleSheet.create({
  voteRow: { flexDirection: "row", gap: 12 },
  voteBtn: { flex: 1, height: 62, borderRadius: 16, borderWidth: 1.5, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10 },
  voteBtnText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 17 },
  multipleButton: { minHeight: 58, borderRadius: 16, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 9, paddingHorizontal: 16 },
  multipleText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 16, textAlign: "center" },
  status: { fontFamily: EditorialFont.mono, fontSize: 11, textAlign: "center", marginTop: 8, minHeight: 15 },
  resultsBtn: { marginTop: 4, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, height: 44, borderRadius: 12, borderWidth: 1.5 },
  resultsText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 14 },
});
