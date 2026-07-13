import React, { useState } from "react";
import { View, Text, Pressable, StyleSheet, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { useVote } from "../hooks/use-vote";
import type { VoteErrorKind } from "../types";
import { SentimentResultsSheet } from "./SentimentResultsSheet";

/**
 * The Agree / Disagree control for a post's support question, wired to the votes domain.
 *
 * One vote per post: the first cast LOCKS the pair — the chosen side stays filled with a tick,
 * the other dims, and both stop responding — mirroring the backend's 409 duplicate-vote rule.
 * On mount it asks whether the caller has already voted, so a post they voted on earlier comes
 * up already locked. Auth/network/other failures show an inline message and leave the buttons
 * live to retry; a duplicate is not an error — it just locks.
 *
 * Colours come from `constants/theme` so it matches the feed's unvoted vote row exactly.
 */
export function VoteControls({ postId, onNextPost }: { postId: number; onNextPost?: () => void }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { loading, myVote, submitting, error, vote } = useVote(postId);
  const [resultsOpen, setResultsOpen] = useState(false);

  const locked = myVote !== null;
  const disabled = loading || submitting || locked;

  const agree = buttonStyle("agree", myVote, e);
  const disagree = buttonStyle("disagree", myVote, e);

  const handleVote = async (voteFor: boolean) => {
    if (await vote(voteFor)) {
      setResultsOpen(true);
    }
  };

  return (
    <View>
      <View style={styles.voteRow}>
        <Pressable
          testID="vote-agree"
          accessibilityRole="button"
          accessibilityLabel="Agree"
          accessibilityState={{ disabled, selected: myVote === true }}
          disabled={disabled}
          onPress={() => void handleVote(true)}
          style={[styles.voteBtn, { backgroundColor: agree.bg, borderColor: agree.border, opacity: agree.opacity }]}
        >
          <Ionicons name={myVote === true ? "checkmark-circle" : "thumbs-up"} size={19} color={agree.fg} />
          <Text style={[styles.voteBtnText, { color: agree.fg }]}>Agree</Text>
        </Pressable>

        <Pressable
          testID="vote-disagree"
          accessibilityRole="button"
          accessibilityLabel="Disagree"
          accessibilityState={{ disabled, selected: myVote === false }}
          disabled={disabled}
          onPress={() => void handleVote(false)}
          style={[styles.voteBtn, { backgroundColor: disagree.bg, borderColor: disagree.border, opacity: disagree.opacity }]}
        >
          {submitting ? (
            <ActivityIndicator testID="vote-submitting" size="small" color={disagree.fg} />
          ) : (
            <Ionicons name={myVote === false ? "checkmark-circle" : "thumbs-down"} size={19} color={disagree.fg} />
          )}
          <Text style={[styles.voteBtnText, { color: disagree.fg }]}>Disagree</Text>
        </Pressable>
      </View>

      {/* One reserved caption line so locking/erroring never shifts the layout below it. */}
      <Text
        testID="vote-status"
        style={[styles.status, { color: error ? e.coral : e.muted }]}
        numberOfLines={1}
      >
        {captionFor({ locked, myVote, submitting, error })}
      </Text>

      {/* Results are gated behind voting: the affordance only appears once the vote is locked. */}
      {locked && (
        <Pressable
          testID="see-results"
          accessibilityRole="button"
          onPress={() => setResultsOpen(true)}
          style={[styles.resultsBtn, { borderColor: e.border, backgroundColor: e.surface }]}
        >
          <Ionicons name="stats-chart" size={16} color={e.teal} />
          <Text style={[styles.resultsText, { color: e.ink }]}>See how others voted</Text>
        </Pressable>
      )}

      <SentimentResultsSheet
        postId={postId}
        visible={resultsOpen}
        onClose={() => setResultsOpen(false)}
        onNextPost={onNextPost}
      />
    </View>
  );
}

/** The resolved colours + opacity for one side, given the caller's stance so far. */
function buttonStyle(
  side: "agree" | "disagree",
  myVote: boolean | null,
  e: ReturnType<typeof getEditorial>
) {
  const isChosen = (side === "agree" && myVote === true) || (side === "disagree" && myVote === false);
  const locked = myVote !== null;

  if (locked && !isChosen) {
    // The rejected side: muted and recessed.
    return { bg: e.surface, border: e.border, fg: e.muted, opacity: 0.55 };
  }
  if (side === "agree") {
    // Agree is a solid fill both before and after it is chosen.
    return { bg: e.voteAgreeFill, border: e.voteAgreeFill, fg: e.voteAgreeOnFill, opacity: 1 };
  }
  // Disagree is outlined until chosen, then fills coral to read as selected.
  return isChosen
    ? { bg: e.coral, border: e.coral, fg: "#FFFFFF", opacity: 1 }
    : { bg: e.voteDisagreeFill, border: e.coral, fg: e.coral, opacity: 1 };
}

function captionFor({
  locked,
  myVote,
  submitting,
  error,
}: {
  locked: boolean;
  myVote: boolean | null;
  submitting: boolean;
  error: VoteErrorKind | null;
}): string {
  if (submitting) return "Recording your vote…";
  if (error === "auth") return "Please sign in again to vote.";
  if (error === "network") return "No connection. Tap to try again.";
  if (error === "unknown") return "Couldn't record your vote. Tap again.";
  if (locked) return `You voted — ${myVote ? "Agree" : "Disagree"}`;
  return " ";
}

const styles = StyleSheet.create({
  voteRow: {
    flexDirection: "row",
    gap: 12,
  },
  voteBtn: {
    flex: 1,
    height: 62,
    borderRadius: 16,
    borderWidth: 1.5,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
  },
  voteBtnText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 17,
  },
  status: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
    textAlign: "center",
    marginTop: 8,
    minHeight: 15,
  },
  resultsBtn: {
    marginTop: 4,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    height: 44,
    borderRadius: 12,
    borderWidth: 1.5,
  },
  resultsText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 14,
  },
});
