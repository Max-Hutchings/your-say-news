import React, { useMemo, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Pressable,
  Animated,
  useWindowDimensions,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useTheme, getEditorial, EditorialFont, feedMediaHeight } from "@/constants/theme";
import { VoteControls } from "@/features/votes";
import type { Post } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";
import { PostVideo } from "./PostVideo";
import { PostImageCarousel } from "./PostImageCarousel";
import { ScrollableSummary } from "./ScrollableSummary";

/**
 * One story, sized to fill a single screen in the immersive feed — the whole post is shown here,
 * there is no detail screen to tap into.
 *
 * There are two layouts, chosen by the media's orientation:
 *
 * - LANDSCAPE / text-only — a stacked card: a 16:9 media box on top, then the headline, a scrolling
 *   summary, the motion and the vote below it. Everything is visible at once.
 * - PORTRAIT — immersive: a headline band sits above a tall 4:5 media box (they never overlap), with
 *   the support question and vote pinned below, always visible. The rest of the story (summary + the
 *   case-for/against cards) is hidden behind a "See more" that slides a panel up over the media;
 *   "See less" drops it back.
 *
 * `isActive` marks this as the on-screen post so its video autoplays; `height` is the feed viewport
 * height so every card is exactly one screen tall.
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
  const isPortrait = (video ?? images[0])?.orientation === "PORTRAIT";
  const immersive = hasMedia && isPortrait;

  // Immersive portrait only: the story panel rises over the video on "See more".
  const [expanded, setExpanded] = useState(false);
  // The media fills whatever height is left above the fixed headline/question/vote block; we measure
  // that space so the media components (which need concrete pixel sizes) fill it exactly — no dead gap.
  const [mediaBox, setMediaBox] = useState({ w: 0, h: 0 });
  const reveal = useMemo(() => new Animated.Value(0), []);
  const toggle = (next: boolean) => {
    setExpanded(next);
    Animated.timing(reveal, { toValue: next ? 1 : 0, duration: 260, useNativeDriver: true }).start();
  };

  // The case-for / case-against cards. Shared by both layouts.
  const caseFooter =
    post.caseFor || post.caseAgainst ? (
      <View style={styles.caseStack}>
        {post.caseFor && (
          <View style={[styles.caseCard, { backgroundColor: e.voteAgreeBg, borderColor: e.voteAgreeBorder }]}>
            <View style={[styles.caseIcon, { backgroundColor: e.teal }]}>
              <Ionicons name="checkmark" size={11} color="#FFFFFF" />
            </View>
            <View style={styles.caseTextWrap}>
              <Text style={[styles.caseLabel, { color: e.teal }]}>THE CASE FOR</Text>
              <Text style={[styles.caseBody, { color: e.caseForText }]}>{post.caseFor}</Text>
            </View>
          </View>
        )}
        {post.caseAgainst && (
          <View style={[styles.caseCard, { backgroundColor: e.voteDisagreeBg, borderColor: e.voteDisagreeBorder }]}>
            <View style={[styles.caseIcon, { backgroundColor: e.coral }]}>
              <Ionicons name="close" size={11} color="#FFFFFF" />
            </View>
            <View style={styles.caseTextWrap}>
              <Text style={[styles.caseLabel, { color: e.coral }]}>THE CASE AGAINST</Text>
              <Text style={[styles.caseBody, { color: e.caseAgainstText }]}>{post.caseAgainst}</Text>
            </View>
          </View>
        )}
      </View>
    ) : null;

  // The motion — the support question. Shared by both layouts.
  const motionBox = (
    <View style={[styles.motionBox, { borderColor: e.motionBorder }]}>
      <Text style={[styles.motionQuote, { color: e.lime }]}>&ldquo;</Text>
      <Text style={[styles.motionText, { color: e.ink }]} numberOfLines={immersive ? 2 : 4}>
        {post.supportQuestion}
      </Text>
    </View>
  );

  // The vote — always visible. The votes domain owns the interaction, locked state and errors.
  const voteRow = <VoteControls postId={post.id} />;

  // Two fixed shapes: a tall 4:5 box for the immersive portrait layout, a wide 16:9 box otherwise.
  const mediaBoxHeight = feedMediaHeight(immersive ? "PORTRAIT" : "LANDSCAPE", window.width);
  const mediaContent = video?.url ? (
    <PostVideo
      uri={video.url}
      posterUri={video.posterUrl}
      isActive={isActive}
      width={window.width}
      height={mediaBoxHeight}
    />
  ) : (
    <PostImageCarousel images={images} width={window.width} height={mediaBoxHeight} />
  );

  // ── Immersive portrait: media fills the space above a fixed headline + question + vote block. ────
  if (immersive) {
    const immersiveMedia = video?.url ? (
      <PostVideo uri={video.url} posterUri={video.posterUrl} isActive={isActive} width={mediaBox.w} height={mediaBox.h} />
    ) : (
      <PostImageCarousel images={images} width={mediaBox.w} height={mediaBox.h} />
    );
    return (
      <View style={[styles.card, { height: cardHeight, backgroundColor: e.bg }]}>
        {/* Media fills the leftover space above the body and is measured so it never leaves a gap.
            The story panel slides up over it on "See more" — kept mounted so the read is instant. */}
        <View
          style={styles.immersiveMedia}
          onLayout={(ev) => {
            const { width: w, height: h } = ev.nativeEvent.layout;
            setMediaBox((prev) => (prev.w === w && prev.h === h ? prev : { w, h }));
          }}
        >
          {mediaBox.h > 0 && immersiveMedia}

          {post.isUnbiased && (
            <View style={styles.badgeOverlay}>
              <UnbiasedBadge />
            </View>
          )}
          <View style={[styles.timeOverlay, { backgroundColor: e.mediaScrim }]}>
            <Text style={[styles.timeOverlayText, { color: e.onMedia }]}>{timeAgo(post.createdAt)}</Text>
          </View>

          {/* Falls back to cardHeight before the media area is measured so the panel stays hidden. */}
          <Animated.View
            style={[
              styles.storyPanel,
              {
                backgroundColor: e.bg,
                transform: [
                  { translateY: reveal.interpolate({ inputRange: [0, 1], outputRange: [mediaBox.h || cardHeight, 0] }) },
                ],
              },
            ]}
          >
            <Pressable style={styles.panelHandle} onPress={() => toggle(false)} accessibilityRole="button">
              <Ionicons name="chevron-down" size={18} color={e.muted} />
              <Text style={[styles.panelHandleText, { color: e.muted }]}>See less</Text>
            </Pressable>
            <ScrollableSummary text={post.summary} footer={caseFooter} />
          </Animated.View>
        </View>

        {/* Fixed under the media — headline, support question, See more and the vote. Never pushed off. */}
        <View style={styles.immersiveBody}>
          <Text style={[styles.title, { color: e.ink }]} numberOfLines={2}>
            {post.title}
          </Text>
          {motionBox}
          {!expanded && (
            <Pressable
              style={[styles.seeMorePill, { borderColor: e.border, backgroundColor: e.surface }]}
              onPress={() => toggle(true)}
              accessibilityRole="button"
            >
              <Text style={[styles.seeMoreText, { color: e.ink }]}>See more</Text>
              <Ionicons name="chevron-up" size={15} color={e.ink} />
            </Pressable>
          )}
          {voteRow}
        </View>
      </View>
    );
  }

  // ── Stacked: landscape media (16:9) or text-only, with everything visible at once. ──────────────
  return (
    <View style={[styles.card, { height: cardHeight, backgroundColor: e.bg }]}>
      {hasMedia && (
        <View style={{ width: window.width, height: mediaBoxHeight }}>
          {mediaContent}

          {post.isUnbiased && (
            <View style={styles.badgeOverlay}>
              <UnbiasedBadge />
            </View>
          )}

          {/* Time posted, over the media bottom-left. */}
          <View style={[styles.timeOverlay, { backgroundColor: e.mediaScrim }]}>
            <Text style={[styles.timeOverlayText, { color: e.onMedia }]}>{timeAgo(post.createdAt)}</Text>
          </View>
        </View>
      )}

      <View style={styles.body}>
        {/* Text-only posts have no media to overlay, so the meta shows here. */}
        {!hasMedia && (
          <View style={styles.metaRow}>
            {post.isUnbiased ? <UnbiasedBadge /> : <View />}
            <Text style={[styles.meta, { color: e.muted }]}>{timeAgo(post.createdAt)}</Text>
          </View>
        )}

        <Text style={[styles.title, { color: e.ink }]} numberOfLines={hasMedia ? 3 : 4}>
          {post.title}
        </Text>

        {/* The scroll region: summary, then the case-for/against cards at the bottom. */}
        <ScrollableSummary text={post.summary} footer={caseFooter} />

        {motionBox}
        {voteRow}
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
    paddingTop: 12,
    paddingBottom: 18,
    gap: 11,
  },
  // ── Immersive portrait ──
  immersiveMedia: {
    flex: 1,
    width: "100%",
    overflow: "hidden",
  },
  immersiveBody: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 18,
    gap: 10,
  },
  storyPanel: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 24,
  },
  panelHandle: {
    alignSelf: "center",
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingVertical: 8,
    marginBottom: 4,
  },
  panelHandleText: {
    fontFamily: EditorialFont.monoSemiBold,
    fontSize: 11,
    letterSpacing: 0.6,
  },
  seeMorePill: {
    alignSelf: "flex-start",
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderWidth: 1.5,
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 7,
  },
  seeMoreText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 13,
  },
  // ── Stacked (landscape / text-only) ──
  badgeOverlay: {
    position: "absolute",
    left: 11,
    top: 11,
  },
  timeOverlay: {
    position: "absolute",
    left: 11,
    bottom: 11,
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 6,
  },
  timeOverlayText: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
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
  motionBox: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 18,
    paddingVertical: 16,
  },
  motionQuote: {
    fontFamily: EditorialFont.serif,
    fontSize: 34,
    lineHeight: 34,
    marginTop: 2,
  },
  motionText: {
    flex: 1,
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 21,
    lineHeight: 27,
  },
  caseStack: {
    gap: 9,
    marginTop: 16,
  },
  caseCard: {
    flexDirection: "row",
    gap: 10,
    paddingHorizontal: 12,
    paddingVertical: 11,
    borderRadius: 11,
    borderWidth: 1,
  },
  caseIcon: {
    width: 18,
    height: 18,
    borderRadius: 9,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 1,
  },
  caseTextWrap: {
    flex: 1,
  },
  caseLabel: {
    fontFamily: EditorialFont.monoSemiBold,
    fontSize: 9,
    fontWeight: "600",
    letterSpacing: 0.9,
    marginBottom: 3,
  },
  caseBody: {
    fontFamily: EditorialFont.sans,
    fontSize: 12.5,
    lineHeight: 18,
  },
});
