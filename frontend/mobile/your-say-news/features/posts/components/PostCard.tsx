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
import { useRouter, type Href } from "expo-router";
import { useTheme, getEditorial, EditorialFont, feedMediaHeight } from "@/constants/theme";
import { VoteControls } from "@/features/votes";
import type { Post } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";
import { PostVideo } from "./PostVideo";
import { PostImageCarousel } from "./PostImageCarousel";
import { ScrollableSummary } from "./ScrollableSummary";

const VIDEO_SOUND_BOTTOM_INSET = 52;

/**
 * One story, sized to fill a single screen in the immersive feed — the whole post is shown here,
 * there is no detail screen to tap into.
 *
 * There are two layouts, chosen by the media's orientation:
 *
 * - LANDSCAPE / text-only — a stacked card: a 16:9 media box on top, then the support question as
 *   the heading, a scrolling summary and the vote below it. Everything is visible at once.
 * - PORTRAIT — immersive: a tall 4:5 media box sits above the support question and vote, which stay
 *   pinned below and always visible. The rest of the story (summary + the
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
  onNextPost,
}: {
  post: Post;
  isActive?: boolean;
  height?: number;
  onNextPost?: () => void;
}) {
  const router = useRouter();
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
  // The media fills whatever height is left above the fixed question/vote block; we measure
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
  const voteRow = <VoteControls postId={post.id} votingType={post.votingType}
    options={post.voteOptions} supportQuestion={post.supportQuestion} onNextPost={onNextPost} />;
  const authorLink = (overMedia = false) => (
    <Pressable
      style={[
        styles.authorPill,
        overMedia && styles.authorOverlay,
        {
          borderColor: overMedia ? e.mediaScrim : e.border,
          backgroundColor: overMedia ? e.mediaScrim : e.surface,
        },
      ]}
      onPress={() => router.push(`/profiles/${post.userId}` as Href)}
      accessibilityRole="button"
      accessibilityLabel="Open author profile"
    >
      <Ionicons name="person-circle-outline" size={15} color={overMedia ? e.onMedia : e.ink} />
      <Text style={[styles.authorText, { color: overMedia ? e.onMedia : e.ink }]}>Author {post.userId}</Text>
    </Pressable>
  );

  // Two fixed shapes: a tall 4:5 box for the immersive portrait layout, a wide 16:9 box otherwise.
  const mediaBoxHeight = feedMediaHeight(immersive ? "PORTRAIT" : "LANDSCAPE", window.width);
  const mediaContent = video?.url ? (
    <PostVideo
      uri={video.url}
      posterUri={video.posterUrl}
      isActive={isActive}
      width={window.width}
      height={mediaBoxHeight}
      controlsBottomInset={VIDEO_SOUND_BOTTOM_INSET}
    />
  ) : (
    <PostImageCarousel images={images} width={window.width} height={mediaBoxHeight} />
  );

  // ── Immersive portrait: media fills the space above a fixed question + vote block. ──────────────
  if (immersive) {
    const immersiveMedia = video?.url ? (
      <PostVideo
        uri={video.url}
        posterUri={video.posterUrl}
        isActive={isActive}
        width={mediaBox.w}
        height={mediaBox.h}
        controlsBottomInset={VIDEO_SOUND_BOTTOM_INSET}
      />
    ) : (
      <PostImageCarousel images={images} width={mediaBox.w} height={mediaBox.h} />
    );
    return (
      <View style={[styles.card, { height: cardHeight, backgroundColor: e.bg }]}>
        {/* Media fills the leftover space above the body and is measured so it never leaves a gap.
            The story panel slides up over it on "See more" — kept mounted so the read is instant. */}
        <View
          testID="post-media-stage"
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
          {authorLink(true)}

          {/* Falls back to cardHeight before the media area is measured so the panel stays hidden. */}
          <Animated.View
            testID="portrait-story-panel"
            pointerEvents={expanded ? "auto" : "none"}
            accessibilityElementsHidden={!expanded}
            importantForAccessibility={expanded ? "auto" : "no-hide-descendants"}
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

          {!expanded && (
            <View testID="portrait-see-more-slot" style={styles.seeMoreOverlay} pointerEvents="box-none">
              <Pressable
                testID="portrait-see-more"
                style={[
                  styles.seeMorePill,
                  { borderColor: e.mediaScrim, backgroundColor: e.mediaScrim },
                ]}
                onPress={() => toggle(true)}
                accessibilityRole="button"
                accessibilityLabel="See more"
              >
                <Text style={[styles.seeMoreText, { color: e.onMedia }]}>See more</Text>
                <Ionicons name="chevron-up" size={15} color={e.onMedia} />
              </Pressable>
            </View>
          )}
        </View>

        {/* Fixed under the media — only the support question and vote, leaving more height for media. */}
        <View testID="post-card-body" style={styles.immersiveBody}>
          {motionBox}
          {voteRow}
        </View>
      </View>
    );
  }

  // ── Stacked: landscape media (16:9) or text-only, with everything visible at once. ──────────────
  return (
    <View style={[styles.card, { height: cardHeight, backgroundColor: e.bg }]}>
      {hasMedia && (
        <View testID="post-media-stage" style={{ width: window.width, height: mediaBoxHeight }}>
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
          {authorLink(true)}
        </View>
      )}

      <View testID="post-card-body" style={styles.body}>
        {/* Text-only posts have no media to overlay, so the meta shows here. */}
        {!hasMedia && (
          <View style={styles.metaRow}>
            {post.isUnbiased ? <UnbiasedBadge /> : <View />}
            <Text style={[styles.meta, { color: e.muted }]}>{timeAgo(post.createdAt)}</Text>
          </View>
        )}

        {motionBox}
        {!hasMedia && authorLink()}

        {/* The scroll region: summary, then the case-for/against cards at the bottom. */}
        <ScrollableSummary text={post.summary} footer={caseFooter} />

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
    paddingBottom: 8,
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
    paddingBottom: 8,
    gap: 10,
  },
  storyPanel: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 4,
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
    alignSelf: "center",
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
  seeMoreOverlay: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 11,
    zIndex: 3,
    alignItems: "center",
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
  authorPill: {
    alignSelf: "flex-start",
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 10,
    paddingVertical: 5,
  },
  authorText: {
    fontFamily: EditorialFont.monoSemiBold,
    fontSize: 10,
    fontWeight: "600",
  },
  authorOverlay: {
    position: "absolute",
    right: 12,
    bottom: 12,
    zIndex: 2,
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
