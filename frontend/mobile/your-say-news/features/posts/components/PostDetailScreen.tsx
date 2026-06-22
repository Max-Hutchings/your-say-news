import React, { useEffect, useState } from "react";
import { View, StyleSheet, ActivityIndicator } from "react-native";
import { ThemedView } from "@/components/themed-view";
import { ThemedText } from "@/components/themed-text";
import { useTheme, Spacing } from "@/constants/theme";
import { getPost } from "../services/PostService";
import type { Post } from "../types";
import { PostDetail } from "./PostDetail";

/**
 * Screen-level wrapper that loads a single post by id and renders its detail,
 * handling loading, not-found (204 → null) and error states. Keeps the route
 * file thin — it just supplies the id.
 */
export function PostDetailScreen({ id }: { id: number }) {
  const { colors } = useTheme();
  const [post, setPost] = useState<Post | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getPost(id)
      .then((result) => active && setPost(result))
      .catch(() => active && setError("We couldn't load this story."))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [id]);

  if (loading) {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator color={colors.brand.primary} />
      </ThemedView>
    );
  }

  if (error || !post) {
    return (
      <ThemedView style={styles.centered}>
        <View>
          <ThemedText variant="bodyMedium" color="tertiary">
            {error ?? "This story is no longer available."}
          </ThemedText>
        </View>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <PostDetail post={post} />
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: Spacing.base,
  },
});
