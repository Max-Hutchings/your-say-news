import React, { useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text } from "react-native";
import { useRouter, type Href } from "expo-router";
import { EditorialFont, getEditorial, useTheme } from "@/constants/theme";
import { requestUnwrap } from "../services/UnwrappedService";
import { trackAction } from "@/features/telemetry";

/**
 * The large lime Unwrap call to action. Tapping asks the backend to queue generation (it only does
 * so once the post has enough votes), then opens Unwrapped whatever the answer, so a story that
 * already exists stays reachable when the request fails.
 */
export function UnwrapButton({ postId }: { postId: number }) {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [opening, setOpening] = useState(false);

  const unwrap = async () => {
    trackAction("unwrapped.unwrap", postId);
    setOpening(true);
    try {
      await requestUnwrap(postId);
    } catch {
      // Unwrapped explains its own availability; a failed request must not block the reader.
    } finally {
      setOpening(false);
    }
    router.push(`/posts/${postId}/unwrapped` as Href);
  };

  return (
    <Pressable testID="unwrap-button" accessibilityRole="button" accessibilityLabel="Unwrap"
      accessibilityState={{ busy: opening, disabled: opening }} disabled={opening}
      onPress={() => void unwrap()}
      style={[styles.button, { backgroundColor: e.lime }]}>
      {opening
        ? <ActivityIndicator color={e.onLime} />
        : <Text style={[styles.label, { color: e.onLime }]}>Unwrap</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { height: 62, borderRadius: 16, alignItems: "center", justifyContent: "center" },
  label: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 18 },
});
