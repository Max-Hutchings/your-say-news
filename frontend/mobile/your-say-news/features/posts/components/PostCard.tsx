import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { Card } from "@/components/ui";
import { useTheme, Spacing, Typography } from "@/constants/theme";
import type { Post, PostMedia } from "../types";
import { UnbiasedBadge } from "./UnbiasedBadge";

/**
 * Compact post preview for the feed: lead media, headline, summary and the
 * support question. Shows the unbiased badge only when the post is flagged
 * unbiased. Tap-through is the caller's concern (wrap in pressable if needed).
 */

type Props = {
  post: Post;
  onPress?: () => void;
};

/** For video we show its poster frame; for images the image itself. */
function leadImageUri(media: PostMedia | undefined): string | null {
  if (!media) return null;
  if (media.mediaType === "VIDEO") return media.posterUrl;
  return media.url;
}

export function PostCard({ post, onPress }: Props) {
  const { colors } = useTheme();
  const imageUri = leadImageUri(post.media[0]);

  return (
    <Card
      variant="elevated"
      padding="none"
      pressable={Boolean(onPress)}
      onPress={onPress}
    >
      {imageUri && (
        <Image
          source={{ uri: imageUri }}
          style={styles.media}
          contentFit="cover"
          testID="post-card-media"
        />
      )}

      <View style={styles.content}>
        {post.isUnbiased && <UnbiasedBadge />}

        <Text style={[styles.title, { color: colors.text.primary }]}>{post.title}</Text>

        <Text
          style={[styles.summary, { color: colors.text.secondary }]}
          numberOfLines={3}
        >
          {post.summary}
        </Text>

        <Text style={[styles.supportQuestion, { color: colors.brand.primary }]}>
          {post.supportQuestion}
        </Text>
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  media: {
    width: "100%",
    height: 200,
  },
  content: {
    padding: Spacing.base,
    gap: Spacing.sm,
  },
  title: {
    ...Typography.h4,
  },
  summary: {
    ...Typography.bodySmall,
  },
  supportQuestion: {
    ...Typography.labelMedium,
    marginTop: Spacing.xs,
  },
});
