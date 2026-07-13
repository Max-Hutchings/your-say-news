import React, { useCallback, useEffect, useRef, useState } from "react";
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
import { useFocusEffect, useRouter, type Href } from "expo-router";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { useAuthStore } from "@/features/auth";
import { getFeed, FEED_PAGE_SIZE } from "../services/PostService";
import type { FeedPostType, Post } from "../types";
import { PostCard } from "./PostCard";
import { Masthead } from "./Masthead";
import { FeedTabs } from "./FeedTabs";
import { FeedTypeFilters } from "./FeedTypeFilters";

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
 *
 * The feed pages: it fetches the first {@link FEED_PAGE_SIZE} posts, then loads the
 * next page as the reader nears the end (see `onEndReached`). A short page means we've
 * reached the bottom, so we stop asking.
 */
export function HomeFeed() {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const email = useAuthStore((s) => s.email);
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewportH, setViewportH] = useState(0);
  const [activeIndex, setActiveIndex] = useState(0);
  const [postType, setPostType] = useState<FeedPostType | null>("VIDEO");

  // Paging cursor and end-of-feed flag. A ref guards against overlapping loadMore calls —
  // onEndReached can fire repeatedly before a request resolves, and React state wouldn't
  // update in time to block the duplicate.
  const page = useRef(0);
  const reachedEnd = useRef(false);
  const loadingMoreRef = useRef(false);
  const pendingNextIndex = useRef<number | null>(null);
  const feedGeneration = useRef(0);
  const listRef = useRef<FlatList<Post>>(null);

  const loadFirst = useCallback(async () => {
    const generation = ++feedGeneration.current;
    setError(null);
    try {
      const first = await getFeed(0, FEED_PAGE_SIZE, postType ?? undefined);
      if (generation !== feedGeneration.current) return;
      setPosts(first);
      page.current = 0;
      reachedEnd.current = first.length < FEED_PAGE_SIZE;
    } catch {
      if (generation === feedGeneration.current) {
        setError("We couldn't load the feed. Pull to try again.");
      }
    } finally {
      if (generation === feedGeneration.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [postType]);

  const loadMore = useCallback(async () => {
    if (loadingMoreRef.current || reachedEnd.current) return;
    const generation = feedGeneration.current;
    loadingMoreRef.current = true;
    setLoadingMore(true);
    try {
      const next = page.current + 1;
      const more = await getFeed(next, FEED_PAGE_SIZE, postType ?? undefined);
      if (generation !== feedGeneration.current) return;
      if (more.length > 0) {
        // Guard against a page that overlaps what we already hold (e.g. a post added between
        // fetches shifting the window) so keys stay unique.
        setPosts((prev) => {
          const seen = new Set(prev.map((p) => p.id));
          return [...prev, ...more.filter((p) => !seen.has(p.id))];
        });
        page.current = next;
      }
      if (more.length < FEED_PAGE_SIZE) reachedEnd.current = true;
    } catch {
      // Leave the flags as they are so a later scroll retries the same page.
    } finally {
      if (generation === feedGeneration.current) {
        loadingMoreRef.current = false;
        setLoadingMore(false);
      }
    }
  }, [postType]);

  useFocusEffect(
    useCallback(() => {
      loadFirst();
    }, [loadFirst])
  );

  useEffect(() => {
    const nextIndex = pendingNextIndex.current;
    if (nextIndex == null || nextIndex >= posts.length) return;
    listRef.current?.scrollToIndex({ index: nextIndex, animated: true });
    pendingNextIndex.current = null;
  }, [posts.length]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    reachedEnd.current = false;
    loadFirst();
  }, [loadFirst]);

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
  const changePostType = (next: FeedPostType | null) => {
    feedGeneration.current += 1;
    loadingMoreRef.current = false;
    setPostType(next);
    setPosts([]);
    setLoading(true);
    setLoadingMore(false);
    setActiveIndex(0);
    pendingNextIndex.current = null;
    page.current = 0;
    reachedEnd.current = false;
    listRef.current?.scrollToOffset({ offset: 0, animated: false });
  };
  const moveToNextPost = (currentIndex: number) => {
    const nextIndex = currentIndex + 1;
    if (nextIndex < posts.length) {
      listRef.current?.scrollToIndex({ index: nextIndex, animated: true });
      return;
    }
    pendingNextIndex.current = nextIndex;
    void loadMore();
  };

  return (
    <View style={[styles.container, { backgroundColor: e.bg }]}>
      <View style={[styles.masthead, { backgroundColor: e.bg, borderBottomColor: e.border }]}>
        <Masthead avatarLabel={avatarLabel} onPressAvatar={() => router.push("/account" as Href)} />
        <View style={styles.tabs}>
          <FeedTabs />
        </View>
        <View style={styles.typeFilters}>
          <FeedTypeFilters value={postType} onChange={changePostType} />
        </View>
      </View>

      <View testID="home-feed-viewport" style={styles.feed} onLayout={onLayout}>
        {loading || viewportH === 0 ? (
          <View style={styles.centered}>
            <ActivityIndicator color={e.lime} />
          </View>
        ) : (
          <FlatList
            ref={listRef}
            data={posts}
            keyExtractor={(p) => String(p.id)}
            renderItem={({ item, index }) => (
              <PostCard
                post={item}
                isActive={index === activeIndex}
                height={viewportH}
                onNextPost={() => moveToNextPost(index)}
              />
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
            onEndReached={loadMore}
            // Items are one viewport tall, so a threshold of one viewport-length fires this
            // as the reader reaches the second-to-last post — in time to load the next page.
            onEndReachedThreshold={1}
            refreshControl={
              <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={e.muted} />
            }
            ListFooterComponent={
              loadingMore ? (
                <View style={styles.footer}>
                  <ActivityIndicator color={e.lime} />
                </View>
              ) : null
            }
            ListEmptyComponent={
              <View style={[styles.centered, { height: viewportH }]}>
                <Text style={[styles.message, { color: error ? e.coral : e.muted }]}>
                  {error ?? emptyMessage(postType)}
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

function emptyMessage(postType: FeedPostType | null): string {
  if (postType === "VIDEO") return "No video posts yet.";
  if (postType === "ARTICLE") return "No article posts yet.";
  return "No stories yet. Be the first to share one.";
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
  tabs: {
    marginTop: 12,
  },
  typeFilters: {
    marginTop: 7,
  },
  feed: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  footer: {
    paddingVertical: 24,
    alignItems: "center",
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
