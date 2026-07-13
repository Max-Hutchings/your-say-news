import React, { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  FlatList,
  Pressable,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, type Href } from "expo-router";
import { getEditorial, EditorialFont, useTheme } from "@/constants/theme";
import {
  CONNECTIONS_PAGE_SIZE,
  followUser,
  listConnections,
  unfollowUser,
} from "../services/ProfileService";
import type { ConnectionsTab, FollowUser } from "../types";

const TABS: { key: ConnectionsTab; label: string }[] = [
  { key: "followers", label: "Followers" },
  { key: "following", label: "Following" },
];

export function ConnectionsScreen({
  userId,
  initialTab,
}: {
  userId: number;
  initialTab: ConnectionsTab;
}) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  const [tab, setTab] = useState<ConnectionsTab>(initialTab);
  const [users, setUsers] = useState<FollowUser[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // (Re)load the first page whenever the active tab changes.
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    listConnections(userId, tab, 0)
      .then((res) => {
        if (!active) return;
        setUsers(res.items);
        setHasMore(res.hasMore);
        setPage(0);
      })
      .catch(() => active && setError("Couldn't load this list."))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [userId, tab]);

  const loadMore = useCallback(async () => {
    if (loadingMore || loading || !hasMore) return;
    setLoadingMore(true);
    try {
      const next = page + 1;
      const res = await listConnections(userId, tab, next);
      setUsers((prev) => [...prev, ...res.items]);
      setHasMore(res.hasMore);
      setPage(next);
    } catch {
      // Keep what we have; the end-of-list spinner simply stops.
      setHasMore(false);
    } finally {
      setLoadingMore(false);
    }
  }, [userId, tab, page, hasMore, loading, loadingMore]);

  const onToggleFollow = useCallback(
    async (target: FollowUser) => {
      const optimistic = !target.followedByViewer;
      setUsers((prev) =>
        prev.map((u) => (u.id === target.id ? { ...u, followedByViewer: optimistic } : u)),
      );
      try {
        const res = optimistic ? await followUser(target.id) : await unfollowUser(target.id);
        setUsers((prev) =>
          prev.map((u) => (u.id === target.id ? { ...u, followedByViewer: res.following } : u)),
        );
      } catch {
        // Revert on failure.
        setUsers((prev) =>
          prev.map((u) => (u.id === target.id ? { ...u, followedByViewer: target.followedByViewer } : u)),
        );
      }
    },
    [],
  );

  const header = (
    <View style={[styles.header, { backgroundColor: e.bg, borderBottomColor: e.border }]}>
      <View style={styles.topRow}>
        <Pressable onPress={() => router.back()} accessibilityRole="button" style={styles.iconButton}>
          <Ionicons name="chevron-back" size={24} color={e.ink} />
        </Pressable>
        <Text style={[styles.screenTitle, { color: e.ink }]}>Connections</Text>
        <View style={styles.iconButton} />
      </View>
      <View style={styles.tabs}>
        {TABS.map(({ key, label }) => {
          const selected = key === tab;
          return (
            <Pressable
              key={key}
              onPress={() => setTab(key)}
              accessibilityRole="tab"
              accessibilityState={{ selected }}
              style={[styles.tab, selected && { borderBottomColor: e.lime }]}
            >
              <Text style={[styles.tabText, { color: selected ? e.ink : e.muted }]}>{label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );

  return (
    <FlatList
      testID="connections-list"
      style={{ backgroundColor: e.bg }}
      data={users}
      keyExtractor={(u) => String(u.id)}
      renderItem={({ item }) => (
        <ConnectionRow
          user={item}
          onPress={() => router.push(`/profiles/${item.id}` as Href)}
          onToggleFollow={() => onToggleFollow(item)}
        />
      )}
      ListHeaderComponent={header}
      stickyHeaderIndices={[0]}
      onEndReached={loadMore}
      onEndReachedThreshold={0.4}
      ListFooterComponent={
        loadingMore ? <ActivityIndicator style={styles.footer} color={e.lime} /> : null
      }
      ListEmptyComponent={
        loading ? (
          <ActivityIndicator style={styles.footer} color={e.lime} />
        ) : (
          <Text style={[styles.message, { color: error ? e.coral : e.muted }]}>
            {error ?? (tab === "followers" ? "No followers yet." : "Not following anyone yet.")}
          </Text>
        )
      }
    />
  );
}

function ConnectionRow({
  user,
  onPress,
  onToggleFollow,
}: {
  user: FollowUser;
  onPress: () => void;
  onToggleFollow: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={[styles.row, { borderBottomColor: e.border }]}
    >
      <View style={[styles.rowAvatar, { backgroundColor: e.lime }]}>
        <Text style={[styles.rowAvatarText, { color: e.onLime }]}>
          {user.displayName.charAt(0).toUpperCase()}
        </Text>
      </View>
      <View style={styles.rowText}>
        <Text style={[styles.rowName, { color: e.ink }]} numberOfLines={1}>
          {user.displayName}
        </Text>
        <Text style={[styles.rowHandle, { color: e.muted }]} numberOfLines={1}>
          @{user.handle}
        </Text>
      </View>
      <Pressable
        onPress={onToggleFollow}
        accessibilityRole="button"
        accessibilityLabel={user.followedByViewer ? `Unfollow ${user.handle}` : `Follow ${user.handle}`}
        hitSlop={8}
        style={[
          styles.rowFollow,
          { backgroundColor: user.followedByViewer ? e.surface : e.lime, borderColor: e.border },
        ]}
      >
        <Text style={[styles.rowFollowText, { color: user.followedByViewer ? e.ink : e.onLime }]}>
          {user.followedByViewer ? "Following" : "Follow"}
        </Text>
      </Pressable>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingHorizontal: 20,
    paddingTop: 10,
    borderBottomWidth: 1,
  },
  topRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  iconButton: {
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  screenTitle: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 16,
    fontWeight: "700",
  },
  tabs: {
    flexDirection: "row",
    marginTop: 10,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 12,
    borderBottomWidth: 2,
    borderBottomColor: "transparent",
  },
  tabText: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 14,
    fontWeight: "700",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  rowAvatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: "center",
    justifyContent: "center",
  },
  rowAvatarText: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 18,
    fontWeight: "700",
  },
  rowText: {
    flex: 1,
  },
  rowName: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 18,
  },
  rowHandle: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
    marginTop: 2,
  },
  rowFollow: {
    borderWidth: 1,
    borderRadius: 999,
    paddingVertical: 7,
    paddingHorizontal: 16,
  },
  rowFollowText: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 13,
    fontWeight: "700",
  },
  footer: {
    paddingVertical: 20,
  },
  message: {
    fontFamily: EditorialFont.sans,
    fontSize: 14,
    textAlign: "center",
    padding: 24,
  },
});
