import React, { useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, View, type LayoutChangeEvent } from "react-native";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { getPost } from "../services/PostService";
import type { Post } from "../types";
import { PostCard } from "./PostCard";

type LoadState = "loading" | "ready" | "not-found" | "error";

/** Loads the one post addressed by a shared link and displays it at the available screen height. */
export function PostScreen({ postId }: { postId: number }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [post, setPost] = useState<Post | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [viewportHeight, setViewportHeight] = useState(0);

  useEffect(() => {
    let active = true;
    setLoadState("loading");
    setPost(null);

    if (!Number.isInteger(postId) || postId <= 0) {
      setLoadState("not-found");
      return () => {
        active = false;
      };
    }

    getPost(postId)
      .then((loadedPost) => {
        if (!active) return;
        setPost(loadedPost);
        setLoadState(loadedPost ? "ready" : "not-found");
      })
      .catch(() => {
        if (active) setLoadState("error");
      });

    return () => {
      active = false;
    };
  }, [postId]);

  const onLayout = (event: LayoutChangeEvent) => {
    setViewportHeight(event.nativeEvent.layout.height);
  };

  return (
    <View
      testID="post-screen"
      style={[styles.screen, { backgroundColor: e.bg }]}
      onLayout={onLayout}
    >
      {loadState === "ready" && post && viewportHeight > 0 ? (
        <PostCard post={post} height={viewportHeight} isActive />
      ) : loadState === "not-found" ? (
        <Text style={[styles.message, { color: e.muted }]}>Story not found.</Text>
      ) : loadState === "error" ? (
        <Text style={[styles.message, { color: e.coral }]}>We couldn&apos;t load this story.</Text>
      ) : (
        <ActivityIndicator color={e.lime} />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    alignItems: "stretch",
    justifyContent: "center",
  },
  message: {
    fontFamily: EditorialFont.sans,
    fontSize: 14,
    textAlign: "center",
    paddingHorizontal: 32,
  },
});
