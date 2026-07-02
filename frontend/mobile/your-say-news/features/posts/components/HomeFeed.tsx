import React, { useCallback, useState } from "react";
import {
  View,
  Text,
  Pressable,
  StyleSheet,
  FlatList,
  ActivityIndicator,
  RefreshControl,
  type LayoutChangeEvent,
  type ViewToken,
} from "react-native";
import { useFocusEffect, useRouter } from "expo-router";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { useAuthStore } from "@/features/auth";
import { getRecent } from "../services/PostService";
import type { Post } from "../types";
import { PostCard } from "./PostCard";
import { Masthead } from "./Masthead";

// A post counts as on-screen (and autoplays its video) once 80% visible. Kept module-level
// so its identity is stable — FlatList rejects a viewability config that changes between renders.
const VIEWABILITY_CONFIG = { itemVisiblePercentThreshold: 80 };

/**
 * The immersive news feed: a slim masthead, then one story per screen. It's a
 * vertically paged list — you swipe up to the next post, TikTok-style — where
 * every card is exactly the viewport tall and the whole story (including its
 * 2-3 paragraph summary) is shown in place, with no detail screen to tap into.
 * The on-screen post is tracked via viewability so its video autoplays. Loads
 * on focus and supports pull-to-refresh; a lime action opens the composer.
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
  const [viewportH, setViewportH] = useState(0);
  const [activeIndex, setActiveIndex] = useState(0);

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

  // Track which post is on screen so only its video plays. setActiveIndex is stable, so the
  // callback identity stays fixed across renders (FlatList requires this).
  const onViewableItemsChanged = useCallback(
    ({ viewableItems }: { viewableItems: ViewToken[] }) => {
      const first = viewableItems[0];
      if (first?.index != null) setActiveIndex(first.index);
    },
    []
  );

  const onLayout = (ev: LayoutChangeEvent) => setViewportH(ev.nativeEvent.layout.height);
  const avatarLabel = email?.[0]?.toUpperCase();

  return (
    <View style={[styles.container, { backgroundColor: e.bg }]}>
      <View style={[styles.masthead, { backgroundColor: e.bg, borderBottomColor: e.border }]}>
        <Masthead avatarLabel={avatarLabel} />
      </View>

      <View style={styles.feed} onLayout={onLayout}>
        {loading || viewportH === 0 ? (
          <View style={styles.centered}>
            <ActivityIndicator color={e.lime} />
          </View>
        ) : (
          <FlatList
            data={posts}
            keyExtractor={(p) => String(p.id)}
            renderItem={({ item, index }) => (
              <PostCard post={item} isActive={index === activeIndex} height={viewportH} />
            )}
            pagingEnabled
            decelerationRate="fast"
            showsVerticalScrollIndicator={false}
            getItemLayout={(_, index) => ({
              length: viewportH,
              offset: viewportH * index,
              index,
            })}
            onViewableItemsChanged={onViewableItemsChanged}
            viewabilityConfig={VIEWABILITY_CONFIG}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={e.muted} />
            }
            ListEmptyComponent={
              <View style={[styles.centered, { height: viewportH }]}>
                <Text style={[styles.message, { color: error ? e.coral : e.muted }]}>
                  {error ?? "No stories yet. Be the first to share one."}
                </Text>
              </View>
            }
          />
        )}
      </View>

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
  feed: {
    flex: 1,
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
    paddingHorizontal: 32,
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
