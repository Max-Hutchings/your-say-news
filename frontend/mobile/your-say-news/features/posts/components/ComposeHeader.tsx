import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * The composer's top bar (design handoff): a bordered back control, a "NEW POST"
 * mono label, and the lime "Post" action. Replaces the native stack header so
 * the whole screen reads in the editorial voice.
 */
export function ComposeHeader({
  onBack,
  onPost,
  posting,
}: {
  onBack: () => void;
  onPost: () => void;
  posting?: boolean;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <View style={[styles.bar, { borderBottomColor: e.border }]}>
      <Pressable
        onPress={onBack}
        accessibilityRole="button"
        accessibilityLabel="Go back"
        style={[styles.back, { borderColor: e.border }]}
      >
        <Text style={[styles.chevron, { color: e.ink }]}>‹</Text>
      </Pressable>

      <Text style={[styles.title, { color: e.secondary }]}>NEW POST</Text>

      <Pressable
        onPress={onPost}
        accessibilityRole="button"
        disabled={posting}
        style={[styles.post, { backgroundColor: e.lime, opacity: posting ? 0.6 : 1 }]}
      >
        <Text style={[styles.postText, { color: e.onLime }]}>{posting ? "Posting…" : "Post"}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 20,
    paddingTop: 2,
    paddingBottom: 12,
    borderBottomWidth: 1,
  },
  back: {
    width: 34,
    height: 34,
    borderRadius: 11,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  chevron: {
    fontFamily: EditorialFont.serif,
    fontSize: 22,
    lineHeight: 24,
    marginTop: -2,
  },
  title: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
    letterSpacing: 1.7,
  },
  post: {
    height: 34,
    paddingHorizontal: 16,
    borderRadius: 11,
    alignItems: "center",
    justifyContent: "center",
  },
  postText: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 13,
  },
});
