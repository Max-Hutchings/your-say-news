/**
 * Onboarding route — the characteristics wizard, guarded. All wizard logic lives in the feature;
 * this wrapper only guards entry, using the same rules as the protected index so the two cannot
 * drift. A user who reaches this URL directly with a profile already on file (or with a store flag
 * that has failed closed to false) is sent on rather than asked to fill the wizard in again.
 */
import { Redirect, type Href } from "expo-router";
import { useEffect, useState } from "react";
import { View, ActivityIndicator, StyleSheet } from "react-native";
import { useAuthStore, getOnboardingStatus, resolveOnboardingDestination } from "@/features/auth";
import { OnboardingScreen } from "@/features/user-characteristics";
import { useTheme, getEditorial } from "@/constants/theme";

export default function UserCharacteristicsRoute() {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const consentedAt = useAuthStore((s) => s.consentedAt);
  const hasCharacteristics = useAuthStore((s) => s.hasCharacteristics);
  const [serverConfirmed, setServerConfirmed] = useState(false);

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
      return <Redirect href={"/consent" as Href} />;
    case "feed":
      return <Redirect href={"/(protected)" as Href} />;
    case "checking":
      return (
        <View style={[styles.loading, { backgroundColor: e.bg }]}>
          <ActivityIndicator color={e.lime} />
        </View>
      );
    case "characteristics":
      return <OnboardingScreen />;
  }
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
