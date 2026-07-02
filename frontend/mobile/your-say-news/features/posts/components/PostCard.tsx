import React from "react";
import { View, Text, StyleSheet, useWindowDimensions } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { Post } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";
import { PostVideo } from "./PostVideo";
import { PostImageCarousel } from "./PostImageCarousel";
import { ScrollableSummary } from "./ScrollableSummary";

/**
 * One story, sized to fill a single screen in the immersive feed — the whole
 * post is shown here, there is no detail screen to tap into. Media sits at the
 * top (an autoplaying video, or a swipeable image carousel), then the headline,
 * the scrollable 2-3 paragraph summary, the support question and the vote.
 *
 * `isActive` marks this as the on-screen post so its video autoplays; `height`
 * is the feed viewport height so every card is exactly one screen tall.
 */
export function PostCard({
  post,
  isActive = false,
  height,
}: {
  post: Post;
  isActive?: boolean;
  height?: number;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const window = useWindowDimensions();
  const cardHeight = height ?? window.height;

  const video = post.media.find((m) => m.mediaType === "VIDEO");
  const images = post.media.filter((m) => m.mediaType === "IMAGE");
  const hasMedia = Boolean(video) || images.length > 0;
  const mediaHeight = hasMedia ? Math.round(cardHeight * 0.42) : 0;

  return (
    <View style={[styles.card, { height: cardHeight, backgroundColor: e.bg }]}>
      {video?.url ? (
        <PostVideo
          uri={video.url}
          posterUri={video.posterUrl}
          isActive={isActive}
          width={window.width}
          height={mediaHeight}
        />
      ) : images.length > 0 ? (
        <PostImageCarousel images={images} width={window.width} height={mediaHeight} />
      ) : null}

      <View style={styles.body}>
        <View style={styles.metaRow}>
          {post.isUnbiased ? <UnbiasedBadge /> : <View />}
          <Text style={[styles.meta, { color: e.muted }]}>{timeAgo(post.createdAt)}</Text>
        </View>

        <Text style={[styles.title, { color: e.ink }]} numberOfLines={hasMedia ? 3 : 4}>
          {post.title}
        </Text>

        <ScrollableSummary text={post.summary} />

        <View style={[styles.quoteBlock, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
          <Text style={[styles.votingEyebrow, { color: e.muted }]}>YOU&apos;RE VOTING ON THIS</Text>
          <Text style={[styles.supportQuestion, { color: e.ink }]} numberOfLines={2}>
            {post.supportQuestion}
          </Text>
        </View>

        <View style={styles.voteRow}>
          <View style={[styles.voteBtn, { borderColor: e.teal }]}>
            <Text style={[styles.voteBtnText, { color: e.teal }]}>Agree</Text>
          </View>
          <View style={[styles.voteBtn, { borderColor: e.coral }]}>
            <Text style={[styles.voteBtnText, { color: e.coral }]}>Disagree</Text>
          </View>
        </View>
      </View>
    </View>
  );
}

/** Compact "2h" / "5h" / "3d" age from an ISO timestamp. */
function timeAgo(iso: string): string {
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000));
  if (mins < 60) return `${mins}m`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

const styles = StyleSheet.create({
  card: {
    width: "100%",
    overflow: "hidden",
  },
  body: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 14,
    paddingBottom: 18,
    gap: 11,
  },
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
  title: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 26,
    lineHeight: 30,
    letterSpacing: -0.35,
  },
  quoteBlock: {
    borderRadius: 14,
    borderWidth: 1,
    paddingHorizontal: 15,
    paddingTop: 11,
    paddingBottom: 13,
  },
  votingEyebrow: {
    fontFamily: EditorialFont.mono,
    fontSize: 9,
    letterSpacing: 1.4,
    marginBottom: 5,
  },
  supportQuestion: {
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 18,
    lineHeight: 23,
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
});
