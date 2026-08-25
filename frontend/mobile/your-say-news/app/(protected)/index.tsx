/**
 * Home route — thin wrapper and the single routing authority for the protected area. The consent
 * gate comes first (a user must accept the privacy promise before anything else), then the
 * characteristics wizard, then the feed. The ordering rules live in the auth feature
 * (`resolveOnboardingDestination`); this route only fetches status and composes.
 */
import { Redirect, type Href } from "expo-router";
import { useEffect, useState } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useAuthStore, getOnboardingStatus, resolveOnboardingDestination } from "@/features/auth";
import { HomeFeed } from "@/features/posts";
import { useTheme, getEditorial } from "@/constants/theme";

export default function Home() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const consentedAt = useAuthStore((s) => s.consentedAt);
  const hasCharacteristics = useAuthStore((s) => s.hasCharacteristics);
  const [serverConfirmed, setServerConfirmed] = useState(false);

  // A resumed (persisted) session lands here WITHOUT re-running completeLogin, so the onboarding
  // flags in the store can be stale. Re-confirm them with the server on mount before we'd ever
  // send an already-onboarded user back through the wizard. A failed call leaves serverConfirmed
  // false, which holds the user on the spinner rather than re-asking for answers we may hold.
  useEffect(() => {
    let active = true;
    getOnboardingStatus()
      .then((status) => {
        if (!active || !status) {
          return;
        }
        useAuthStore.setState({
          hasCharacteristics: status.hasCharacteristics,
          hasOnboarded: status.onboarded,
        });
        setServerConfirmed(true);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  const destination = resolveOnboardingDestination({
    consentedAt,
    hasCharacteristics,
    serverConfirmed,
  });

  switch (destination) {
    case "consent":
      // Cast: typed-routes regenerate the "/consent" entry on the next `expo start`.
      return <Redirect href={"/consent" as Href} />;
    case "characteristics":
      return <Redirect href={"/usercharacteristics" as Href} />;
    case "checking":
      return (
        <View style={[styles.loading, { backgroundColor: e.bg }]}>
          <ActivityIndicator color={e.lime} />
        </View>
      );
    case "feed":
      return <HomeFeed />;
  }
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
