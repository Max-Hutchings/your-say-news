/**
 * Splash/Loading Screen
 * 
 * Initial loading screen with theme-aware styling
 */

import { useEffect, useState } from "react";
import { Platform, View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { Redirect } from "expo-router";
import {
  useAuthStore,
  type SessionRestoreResult,
} from "@/features/auth";
import { getEditorial, EditorialFont } from "@/constants/theme";

// The pre-auth loading moment shares the sign-in screen's fixed dark brand palette.
const e = getEditorial(true);

const isWeb = Platform.OS === "web";

export default function SplashScreen() {
  const { restoreSession, _stateHydrated } = useAuthStore();
  const [sessionResult, setSessionResult] = useState<SessionRestoreResult | null>(null);

  // Firebase restores its session first, then post-service confirms the PostgreSQL account.
  useEffect(() => {
    if (!_stateHydrated) {
      return;
    }

    let cancelled = false;
    restoreSession()
      .then((result) => {
        if (!cancelled) {
          setSessionResult(result);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSessionResult("unverified");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [restoreSession, _stateHydrated]);

  const checkingSession = sessionResult === null;
  const admitted = sessionResult === "signed-in";

  // Wait for auth state to be hydrated, and for the stored session to be vouched for, before routing
  if ((!isWeb && !_stateHydrated) || checkingSession) {
    return (
      <View style={[styles.container, { backgroundColor: e.bg }]}>
        {/* Brand lockup — lime badge + serif wordmark, matching sign-in */}
        <View style={styles.brandRow}>
          <View style={[styles.logo, { backgroundColor: e.lime }]}>
            <Text style={[styles.logoY, { color: e.onLime }]}>Y</Text>
          </View>
          <Text style={[styles.wordmark, { color: e.ink }]}>Your Say News</Text>
        </View>

        <Text style={[styles.eyebrow, { color: e.muted }]}>NEUTRAL · PEOPLE-POWERED</Text>

        {/* Loading Indicator */}
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="small" color={e.lime} />
          <Text style={[styles.loadingText, { color: e.muted }]}>LOADING</Text>
        </View>
      </View>
    );
  }

  // Use Redirect component instead of programmatic navigation
  if (admitted) {
    return <Redirect href="/(protected)" />;
  }

  return <Redirect href="/sign-in" />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 26,
  },
  brandRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  logo: { width: 36, height: 36, borderRadius: 9, alignItems: "center", justifyContent: "center" },
  logoY: { fontFamily: EditorialFont.serif, fontSize: 24, fontWeight: "600" },
  wordmark: { fontFamily: EditorialFont.serif, fontSize: 24, letterSpacing: -0.2 },
  eyebrow: { fontFamily: EditorialFont.mono, fontSize: 11, letterSpacing: 1.6, marginTop: 18 },
  loadingContainer: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginTop: 40,
  },
  loadingText: { fontFamily: EditorialFont.mono, fontSize: 10.5, letterSpacing: 2 },
});
