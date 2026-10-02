import React from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Redirect, type Href } from "expo-router";
import { getEditorial, useTheme } from "@/constants/theme";
import { useUnwrappedFeatures } from "../hooks/use-unwrapped-features";
import { UnwrappedScreen } from "./UnwrappedScreen";

/**
 * The Unwrapped route's guard. A direct link, browser history or an old bookmark can still reach
 * `/posts/{postId}/unwrapped`, so when the kill switch is off (or the flags cannot be loaded) the
 * reader is replaced onto the results page and Unwrapped is never requested.
 */
export function GatedUnwrappedScreen({ postId }: { postId: number }) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const features = useUnwrappedFeatures();

  if (!features) {
    return (
      <View testID="unwrapped-gate-loading" style={[styles.loading, { backgroundColor: e.bg }]}>
        <ActivityIndicator color={e.teal} />
      </View>
    );
  }
  if (!features.enabled) {
    return <Redirect href={`/posts/${postId}/results` as Href} />;
  }
  return <UnwrappedScreen postId={postId} />;
}

const styles = StyleSheet.create({
  loading: { flex: 1, alignItems: "center", justifyContent: "center" },
});
