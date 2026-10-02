import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { SentimentResults } from "@/features/votes";
import { useUnwrappedFeatures } from "../hooks/use-unwrapped-features";
import { UnwrapButton } from "./UnwrapButton";

/**
 * Where a vote leads unless Unwrapped opens directly: the live vote data, with the Unwrap button
 * on top when Unwrapped is enabled and the Unwrap button flag is on.
 */
export function PostResultsScreen({ postId }: { postId: number }) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const features = useUnwrappedFeatures();
  const showUnwrap = features?.enabled === true && features.unwrapButton;

  return (
    <View style={[styles.root, { backgroundColor: e.bg }]}>
      <View style={[styles.header, { borderBottomColor: e.border }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Back to feed" onPress={() => router.back()}>
          <Ionicons name="close" size={24} color={e.ink} />
        </Pressable>
        <Text style={[styles.title, { color: e.ink }]}>How people voted</Text>
      </View>
      {showUnwrap && (
        <View style={styles.unwrap}>
          <UnwrapButton postId={postId} />
        </View>
      )}
      <SentimentResults postId={postId} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: { minHeight: 64, borderBottomWidth: 1, paddingHorizontal: 20,
    flexDirection: "row", alignItems: "center", gap: 18 },
  title: { fontFamily: EditorialFont.serif, fontSize: 23 },
  unwrap: { paddingHorizontal: 20, paddingTop: 16 },
});
