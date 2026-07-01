import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { Post, PostMedia } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";

/**
 * A story in the feed, in the editorial design language (design handoff).
 *
 * - `lead`: the front-page hero — media banner, serif headline, summary, the
 *   support question promoted into a quote block, and the Agree / Disagree
 *   invitation to vote.
 * - `compact`: a thumbnail row beneath the lead — headline plus the support
 *   question as a tappable quote chip.
 *
 * Casting a vote isn't wired on the client yet, so Agree / Disagree open the
 * post (where voting will live) — same as tapping the card.
 */

type Variant = "lead" | "compact";

type Props = {
  post: Post;
  variant?: Variant;
  onPress?: () => void;
};

/** For video we show its poster frame; for images the image itself. */
function leadImageUri(media: PostMedia | undefined): string | null {
  if (!media) return null;
  if (media.mediaType === "VIDEO") return media.posterUrl;
  return media.url;
}

/** Compact "2h" / "5h" / "3d" age from an ISO timestamp. */
function timeAgo(iso: string): string {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins}m`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

export function PostCard({ post, variant = "lead", onPress }: Props) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const imageUri = leadImageUri(post.media[0]);
  const isVideo = post.media[0]?.mediaType === "VIDEO";

  if (variant === "compact") {
    return (
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        style={[styles.compactCard, { backgroundColor: e.surface, borderColor: e.border }]}
      >
        <View style={[styles.thumb, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
          {imageUri ? (
            <Image
              source={{ uri: imageUri }}
              style={StyleSheet.absoluteFill}
              contentFit="cover"
              testID="post-card-media"
            />
          ) : (
            <Text style={[styles.thumbLabel, { color: e.muted, backgroundColor: e.bg }]}>
              {isVideo ? "VIDEO" : "PHOTO"}
            </Text>
          )}
        </View>

        <View style={styles.compactBody}>
          <View style={styles.metaRow}>
            {post.isUnbiased ? <UnbiasedBadge /> : <View />}
            <Text style={[styles.meta, { color: e.muted }]}>{timeAgo(post.createdAt)}</Text>
          </View>

          <Text style={[styles.compactTitle, { color: e.ink }]} numberOfLines={3}>
            {post.title}
          </Text>

          <View style={[styles.quoteChip, { backgroundColor: e.surfaceAlt }]}>
            <Text style={[styles.quoteMarkSmall, { color: e.muted }]}>{"“"}</Text>
            <Text style={[styles.quoteChipText, { color: e.secondary }]} numberOfLines={2}>
              {post.supportQuestion}
            </Text>
            <Text style={[styles.chevron, { color: e.muted }]}>{"›"}</Text>
          </View>
        </View>
      </Pressable>
    );
  }

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={[styles.leadCard, { backgroundColor: e.surface, borderColor: e.border }]}
    >
      <View style={[styles.banner, { backgroundColor: e.surfaceAlt }]}>
        {imageUri && (
          <Image
            source={{ uri: imageUri }}
            style={StyleSheet.absoluteFill}
            contentFit="cover"
            testID="post-card-media"
          />
        )}
        <View style={[styles.leadTag, { backgroundColor: e.ink }]}>
          <View style={[styles.leadDot, { backgroundColor: e.lime }]} />
          <Text style={[styles.leadTagText, { color: e.bg }]}>TODAY&apos;S LEAD</Text>
        </View>
        {isVideo && (
          <View style={[styles.play, { backgroundColor: e.ink }]}>
            <Text style={[styles.playIcon, { color: e.bg }]}>{"▶"}</Text>
          </View>
        )}
      </View>

      <View style={styles.content}>
        <View style={styles.metaRow}>
          {post.isUnbiased ? <UnbiasedBadge /> : <View />}
          <Text style={[styles.meta, { color: e.muted }]}>{timeAgo(post.createdAt)}</Text>
        </View>

        <Text style={[styles.title, { color: e.ink }]}>{post.title}</Text>

        <Text style={[styles.summary, { color: e.secondary }]}>{post.summary}</Text>

        <View style={[styles.quoteBlock, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
          <Text style={[styles.quoteMark, { color: e.border }]}>{"“"}</Text>
          <Text style={[styles.votingEyebrow, { color: e.muted }]}>YOU&apos;RE VOTING ON THIS</Text>
          <Text style={[styles.supportQuestion, { color: e.ink }]}>{post.supportQuestion}</Text>
        </View>

        <View style={styles.voteRow}>
          <Pressable
            onPress={onPress}
            accessibilityRole="button"
            accessibilityLabel="Agree"
            style={[styles.voteBtn, { borderColor: e.teal }]}
          >
            <Text style={[styles.voteBtnText, { color: e.teal }]}>Agree</Text>
          </Pressable>
          <Pressable
            onPress={onPress}
            accessibilityRole="button"
            accessibilityLabel="Disagree"
            style={[styles.voteBtn, { borderColor: e.coral }]}
          >
            <Text style={[styles.voteBtnText, { color: e.coral }]}>Disagree</Text>
          </Pressable>
        </View>

        <Text style={[styles.reveal, { color: e.muted }]}>
          Vote to reveal how people like you split
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  // shared
  metaRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    minHeight: 18,
  },
  meta: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
  },

  // lead
  leadCard: {
    borderRadius: 18,
    borderWidth: 1,
    overflow: "hidden",
  },
  banner: {
    height: 178,
    alignItems: "center",
    justifyContent: "center",
  },
  leadTag: {
    position: "absolute",
    left: 12,
    top: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: 5,
    paddingHorizontal: 9,
    borderRadius: 7,
  },
  leadDot: {
    width: 5,
    height: 5,
    borderRadius: 2.5,
  },
  leadTagText: {
    fontFamily: EditorialFont.mono,
    fontSize: 9,
    letterSpacing: 1.3,
  },
  play: {
    width: 54,
    height: 54,
    borderRadius: 27,
    alignItems: "center",
    justifyContent: "center",
    opacity: 0.85,
  },
  playIcon: {
    fontSize: 18,
    marginLeft: 3,
  },
  content: {
    paddingHorizontal: 17,
    paddingTop: 15,
    paddingBottom: 18,
    gap: 11,
  },
  title: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 25,
    lineHeight: 28,
    letterSpacing: -0.35,
  },
  summary: {
    fontFamily: EditorialFont.sans,
    fontSize: 13.5,
    lineHeight: 21,
  },
  quoteBlock: {
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 15,
    paddingTop: 16,
    paddingBottom: 14,
  },
  quoteMark: {
    position: "absolute",
    left: 11,
    top: 0,
    fontFamily: EditorialFont.serif,
    fontSize: 44,
  },
  votingEyebrow: {
    fontFamily: EditorialFont.mono,
    fontSize: 9,
    letterSpacing: 1.4,
    marginLeft: 28,
    marginBottom: 7,
  },
  supportQuestion: {
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 20,
    lineHeight: 26,
    paddingLeft: 28,
  },
  voteRow: {
    flexDirection: "row",
    gap: 11,
  },
  voteBtn: {
    flex: 1,
    height: 48,
    borderRadius: 13,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  voteBtnText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 15,
  },
  reveal: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    textAlign: "center",
  },

  // compact
  compactCard: {
    flexDirection: "row",
    gap: 13,
    borderRadius: 16,
    borderWidth: 1,
    padding: 13,
  },
  thumb: {
    width: 90,
    height: 104,
    borderRadius: 11,
    borderWidth: 1,
    overflow: "hidden",
    alignItems: "flex-start",
    justifyContent: "flex-end",
    padding: 7,
  },
  thumbLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 8,
    letterSpacing: 0.8,
    paddingVertical: 2,
    paddingHorizontal: 5,
    borderRadius: 4,
    overflow: "hidden",
  },
  compactBody: {
    flex: 1,
    minWidth: 0,
    gap: 8,
  },
  compactTitle: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 17,
    lineHeight: 20,
  },
  quoteChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingVertical: 7,
    paddingHorizontal: 10,
    borderRadius: 9,
  },
  quoteMarkSmall: {
    fontFamily: EditorialFont.serif,
    fontSize: 18,
  },
  quoteChipText: {
    flex: 1,
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 12.5,
    lineHeight: 16,
  },
  chevron: {
    fontSize: 14,
  },
});
