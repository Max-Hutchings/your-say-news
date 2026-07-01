import React, { useCallback, useState } from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  RefreshControl,
} from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { useAuthStore } from "@/features/auth";
import { getRecent } from "../services/PostService";
import type { Post } from "../types";
import { PostCard } from "./PostCard";
import { Masthead } from "./Masthead";

/**
 * The news feed as an editorial front page (design handoff): a dated masthead,
 * the newest story as the lead, then the rest as compact cards beneath a
 * "MORE STORIES TODAY" rule. Loads on focus (so a freshly published post shows
 * on return) and supports pull-to-refresh. A lime action opens the composer.
 */
export function HomeFeed() {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const email = useAuthStore((s) => s.email);
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

  const [lead, ...rest] = posts;
  const avatarLabel = email?.[0]?.toUpperCase();

  return (
    <View style={[styles.container, { backgroundColor: e.bgFeed }]}>
      <View style={[styles.masthead, { backgroundColor: e.bg, borderBottomColor: e.border }]}>
        <Masthead avatarLabel={avatarLabel} />
      </View>

      {loading ? (
        <View style={styles.centered}>
          <ActivityIndicator color={e.lime} />
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={e.muted} />}
        >
          {error && (
            <Text style={[styles.message, { color: e.coral }]}>{error}</Text>
          )}

          {!error && posts.length === 0 && (
            <Text style={[styles.message, { color: e.muted }]}>
              No stories yet. Be the first to share one.
            </Text>
          )}

          {lead && (
            <PostCard post={lead} variant="lead" onPress={() => router.push(`/post/${lead.id}`)} />
          )}

          {rest.length > 0 && (
            <View style={styles.divider}>
              <View style={[styles.rule, { backgroundColor: e.border }]} />
              <Text style={[styles.dividerLabel, { color: e.muted }]}>MORE STORIES TODAY</Text>
              <View style={[styles.rule, { backgroundColor: e.border }]} />
            </View>
          )}

          {rest.map((post) => (
            <PostCard
              key={post.id}
              post={post}
              variant="compact"
              onPress={() => router.push(`/post/${post.id}`)}
            />
          ))}
        </ScrollView>
      )}

      <Pressable
        onPress={() => router.push("/create-post")}
        accessibilityRole="button"
        accessibilityLabel="New post"
        style={[styles.fab, { backgroundColor: e.lime }]}
      >
        <Text style={[styles.fabText, { color: e.onLime }]}>＋ Post</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  masthead: {
    paddingTop: 8,
    paddingBottom: 11,
    borderBottomWidth: 1,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    padding: 15,
    paddingBottom: 96,
    gap: 13,
  },
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  message: {
    fontFamily: EditorialFont.sans,
    fontSize: 14,
    textAlign: "center",
    paddingVertical: 48,
  },
  divider: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginVertical: 9,
  },
  rule: {
    flex: 1,
    height: 1,
  },
  dividerLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 1.6,
  },
  fab: {
    position: "absolute",
    right: 18,
    bottom: 26,
    paddingVertical: 13,
    paddingHorizontal: 20,
    borderRadius: 24,
    shadowColor: "#000",
    shadowOpacity: 0.25,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  fabText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 15,
  },
});
