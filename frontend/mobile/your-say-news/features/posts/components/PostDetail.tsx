import React from "react";
import { View, Text, StyleSheet, ScrollView } from "react-native";
import { Image } from "expo-image";
import { useTheme, Spacing, Typography, BorderRadius } from "@/constants/theme";
import type { Post, PostMedia } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";

/**
 * Full single-post view: all media, headline, summary and the support
 * question. Video items render their poster frame with a play affordance
 * (full playback arrives with the vote/feed work).
 */

type Props = {
  post: Post;
};

function MediaBlock({ media }: { media: PostMedia }) {
  const { colors } = useTheme();
  const isVideo = media.mediaType === "VIDEO";
  const uri = isVideo ? media.posterUrl : media.url;
  if (!uri) return null;

  return (
    <View style={styles.mediaWrapper}>
      <Image source={{ uri }} style={styles.media} contentFit="cover" />
      {isVideo && (
        <View style={[styles.playOverlay, { backgroundColor: colors.background.overlay }]}>
          <Text style={[styles.playIcon, { color: colors.text.inverse }]}>▶</Text>
        </View>
      )}
    </View>
  );
}

export function PostDetail({ post }: Props) {
  const { colors } = useTheme();

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {post.media.map((media) => (
        <MediaBlock key={media.s3Key} media={media} />
      ))}

      {post.isUnbiased && <UnbiasedBadge />}

      <Text style={[styles.title, { color: colors.text.primary }]}>{post.title}</Text>

      <Text style={[styles.summary, { color: colors.text.secondary }]}>{post.summary}</Text>

      <View
        style={[
          styles.questionCard,
          { backgroundColor: colors.surface.secondary, borderColor: colors.border.primary },
        ]}
      >
        <Text style={[styles.questionLabel, { color: colors.text.tertiary }]}>
          What&apos;s your take?
        </Text>
        <Text style={[styles.supportQuestion, { color: colors.text.primary }]}>
          {post.supportQuestion}
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: Spacing.base,
    gap: Spacing.base,
  },
  mediaWrapper: {
    borderRadius: BorderRadius.lg,
    overflow: "hidden",
  },
  media: {
    width: "100%",
    height: 240,
  },
  playOverlay: {
    ...StyleSheet.absoluteFill,
    alignItems: "center",
    justifyContent: "center",
  },
  playIcon: {
    ...Typography.h2,
  },
  title: {
    ...Typography.h2,
  },
  summary: {
    ...Typography.bodyMedium,
  },
  questionCard: {
    padding: Spacing.base,
    borderRadius: BorderRadius.lg,
    borderWidth: 1,
    gap: Spacing.xs,
  },
  questionLabel: {
    ...Typography.overline,
    letterSpacing: 1,
  },
  supportQuestion: {
    ...Typography.h4,
  },
});
