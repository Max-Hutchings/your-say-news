/**
 * Home route — thin wrapper and the single routing authority for the protected area. The consent
 * gate stays here (first-time users must accept the privacy promise), then the characteristics
 * wizard, then the feed. The feed itself lives in the posts feature.
 */
import { Redirect, type Href } from "expo-router";
import { useEffect, useState } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useAuthStore, getOnboardingStatus } from "@/features/auth";
import { HomeFeed } from "@/features/posts";
import { useTheme, getEditorial } from "@/constants/theme";

export default function Home() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const consentedAt = useAuthStore((s) => s.consentedAt);
  const hasCharacteristics = useAuthStore((s) => s.hasCharacteristics);
  const [checked, setChecked] = useState(false);

  // A resumed (persisted) session lands here WITHOUT re-running completeLogin, so the onboarding
  // flags in the store can be stale. Re-confirm them with the server on mount before we'd ever
  // send an already-onboarded user back through the wizard.
  useEffect(() => {
    let active = true;
    getOnboardingStatus()
      .then((status) => {
        if (active && status) {
          useAuthStore.setState({
            hasCharacteristics: status.hasCharacteristics,
            hasOnboarded: status.onboarded,
          });
        }
      })
      .finally(() => {
        if (active) setChecked(true);
      });
    return () => {
      active = false;
    };
  }, []);

  // First-time users must read the privacy promise and consent before anything else.
  if (!consentedAt) {
    // Cast: typed-routes regenerate the "/consent" entry on the next `expo start`.
    return <Redirect href={"/consent" as Href} />;
  }

  // Only route to the wizard once the server has confirmed there's no profile. If the store already
  // knows they have one we go straight through; otherwise wait for the check rather than bounce them.
  if (!hasCharacteristics && !checked) {
    return (
      <View style={[styles.loading, { backgroundColor: e.bg }]}>
        <ActivityIndicator color={e.lime} />
      </View>
    );
  }

  if (!hasCharacteristics) {
    return <Redirect href={"/usercharacteristics" as Href} />;
  }

  return <HomeFeed />;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
