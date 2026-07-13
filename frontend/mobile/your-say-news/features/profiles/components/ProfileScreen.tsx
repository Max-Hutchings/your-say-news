import React, { useCallback, useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, type Href } from "expo-router";
import { getEditorial, EditorialFont, useTheme } from "@/constants/theme";
import { PostCard, listByUser, type Post } from "@/features/posts";
import { followUser, getMyProfile, getProfile, unfollowUser } from "../services/ProfileService";
import type { PublicProfile } from "../types";

export function ProfileScreen({ userId }: { userId?: number }) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [profile, setProfile] = useState<PublicProfile | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const nextProfile = userId ? await getProfile(userId) : await getMyProfile();
      setProfile(nextProfile);
      setPosts(nextProfile ? await listByUser(nextProfile.id) : []);
    } catch {
      setError("Profile unavailable.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [userId]);

  useEffect(() => {
    load();
  }, [load]);

  const toggleFollow = async () => {
    if (!profile) return;
    const next = profile.followedByViewer
      ? await unfollowUser(profile.id)
      : await followUser(profile.id);
    setProfile({
      ...profile,
      followedByViewer: next.following,
      followerCount: next.followerCount,
      followingCount: next.followingCount,
    });
  };

  const onRefresh = () => {
    setRefreshing(true);
    load();
  };

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: e.bg }]}>
        <ActivityIndicator color={e.lime} />
      </View>
    );
  }

  const header = (
    <View style={[styles.header, { backgroundColor: e.bg, borderBottomColor: e.border }]}>
      <View style={styles.topRow}>
        <Pressable onPress={() => router.back()} accessibilityRole="button" style={styles.iconButton}>
          <Ionicons name="chevron-back" size={24} color={e.ink} />
        </Pressable>
        <Text style={[styles.screenTitle, { color: e.ink }]}>Profile</Text>
        <View style={styles.iconButton} />
      </View>

      {profile ? (
        <>
          <View style={styles.identityRow}>
            <View style={[styles.avatar, { backgroundColor: e.lime }]}>
              <Text style={[styles.avatarText, { color: e.onLime }]}>
                {profile.displayName.charAt(0).toUpperCase()}
              </Text>
            </View>
            <View style={styles.identityText}>
              <Text style={[styles.name, { color: e.ink }]} numberOfLines={1}>
                {profile.displayName}
              </Text>
              <Text style={[styles.handle, { color: e.muted }]}>@{profile.handle}</Text>
            </View>
          </View>

          <View style={styles.statsRow}>
            <Stat label="Posts" value={posts.length} />
            <Stat
              label="Followers"
              value={profile.followerCount}
              onPress={() =>
                router.push(`/profiles/${profile.id}/connections?tab=followers` as Href)
              }
            />
            <Stat
              label="Following"
              value={profile.followingCount}
              onPress={() =>
                router.push(`/profiles/${profile.id}/connections?tab=following` as Href)
              }
            />
          </View>

          {userId && (
            <Pressable
              onPress={toggleFollow}
              accessibilityRole="button"
              style={[
                styles.followButton,
                { backgroundColor: profile.followedByViewer ? e.surface : e.lime, borderColor: e.border },
              ]}
            >
              <Text style={[styles.followText, { color: profile.followedByViewer ? e.ink : e.onLime }]}>
                {profile.followedByViewer ? "Following" : "Follow"}
              </Text>
            </Pressable>
          )}
        </>
      ) : (
        <Text style={[styles.message, { color: e.coral }]}>{error ?? "Profile not found."}</Text>
      )}
    </View>
  );

  return (
    <FlatList
      style={{ backgroundColor: e.bg }}
      data={posts}
      keyExtractor={(p) => String(p.id)}
      renderItem={({ item }) => <PostCard post={item} height={620} />}
      ListHeaderComponent={header}
      ListEmptyComponent={
        <Text style={[styles.message, { color: e.muted }]}>No posts yet.</Text>
      }
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={e.muted} />}
    />
  );
}

function Stat({
  label,
  value,
  onPress,
}: {
  label: string;
  value: number;
  onPress?: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  return (
    <Pressable
      style={styles.stat}
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? "button" : undefined}
      accessibilityLabel={onPress ? `${value} ${label}` : undefined}
    >
      <Text style={[styles.statValue, { color: e.ink }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: e.muted }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 10,
    paddingBottom: 20,
    borderBottomWidth: 1,
    gap: 18,
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
  identityRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
  },
  avatar: {
    width: 62,
    height: 62,
    borderRadius: 31,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarText: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 26,
    fontWeight: "700",
  },
  identityText: {
    flex: 1,
  },
  name: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 28,
  },
  handle: {
    fontFamily: EditorialFont.mono,
    fontSize: 12,
    marginTop: 3,
  },
  statsRow: {
    flexDirection: "row",
    gap: 26,
  },
  stat: {
    gap: 2,
  },
  statValue: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 18,
    fontWeight: "700",
  },
  statLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
  },
  followButton: {
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderRadius: 999,
    paddingVertical: 12,
  },
  followText: {
    fontFamily: EditorialFont.sansBold,
    fontSize: 15,
    fontWeight: "700",
  },
  message: {
    fontFamily: EditorialFont.sans,
    fontSize: 14,
    textAlign: "center",
    padding: 24,
  },
});
