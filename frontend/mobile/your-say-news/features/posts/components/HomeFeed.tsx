import React, { useCallback, useState } from "react";
import { View, StyleSheet, ScrollView, ActivityIndicator, RefreshControl } from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import { Button } from "@/components/ui";
import { ThemedView } from "@/components/themed-view";
import { ThemedText } from "@/components/themed-text";
import { useTheme, Spacing } from "@/constants/theme";
import { getRecent } from "../services/PostService";
import type { Post } from "../types";
import { PostCard } from "./PostCard";

/**
 * The home feed: recent posts as cards, with a create-post entry point. Loads
 * on focus (so a freshly published post shows on return) and supports
 * pull-to-refresh. Tapping a card opens its detail screen.
 */
export function HomeFeed() {
  const router = useRouter();
  const { colors } = useTheme();
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setPosts(await getRecent());
    } catch {
      setError("We couldn't load the feed. Pull to try again.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    load();
  }, [load]);

  return (
    <ThemedView style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <ThemedText variant="h3">News Feed</ThemedText>
          <ThemedText variant="bodySmall" color="secondary">
            Read a story and share your perspective
          </ThemedText>
        </View>
        <Button size="sm" onPress={() => router.push("/create-post")}>
          New post
        </Button>
      </View>

      {loading ? (
        <View style={styles.centered}>
          <ActivityIndicator color={colors.brand.primary} />
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        >
          {error && (
            <ThemedText variant="bodySmall" color="error" style={styles.message}>
              {error}
            </ThemedText>
          )}

          {!error && posts.length === 0 && (
            <ThemedText variant="bodyMedium" color="tertiary" style={styles.message}>
              No stories yet. Be the first to share one.
            </ThemedText>
          )}

          {posts.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              onPress={() => router.push(`/post/${post.id}`)}
            />
          ))}
        </ScrollView>
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: Spacing.base,
    paddingTop: Spacing.xl,
    paddingBottom: Spacing.base,
    gap: Spacing.base,
  },
  headerText: {
    flex: 1,
    gap: Spacing.xs,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing.base,
    gap: Spacing.lg,
  },
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  message: {
    textAlign: "center",
    paddingVertical: Spacing["2xl"],
  },
});
