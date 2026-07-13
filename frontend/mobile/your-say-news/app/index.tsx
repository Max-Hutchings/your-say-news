/**
 * Splash/Loading Screen
 * 
 * Initial loading screen with theme-aware styling
 */

import { useEffect, useState } from "react";
import { Platform, View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { Redirect } from "expo-router";
import { completeKeycloakWebRedirectFromUrl, useAuthStore } from "@/features/auth";
import { getEditorial, EditorialFont } from "@/constants/theme";

// The pre-auth loading moment shares the sign-in screen's fixed dark brand palette.
const e = getEditorial(true);

function hasAuthRedirectParams(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const url = new URL(window.location.href);
  return url.searchParams.has("code") && url.searchParams.has("state");
}

const isWeb = Platform.OS === "web";

export default function SplashScreen() {
  const { completeLogin, isLoggedIn, _stateHydrated } = useAuthStore();
  const [processingAuthRedirect, setProcessingAuthRedirect] = useState(hasAuthRedirectParams);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }

    const url = new URL(window.location.href);
    if (!url.searchParams.has("code") || !url.searchParams.has("state")) {
      return;
    }

    if (isLoggedIn) {
      window.history.replaceState({}, document.title, window.location.pathname);
      queueMicrotask(() => setProcessingAuthRedirect(false));
      return;
    }

    let cancelled = false;
    completeKeycloakWebRedirectFromUrl(window.location.href)
      .then(async (tokens) => {
        if (!cancelled && tokens) {
          const loggedIn = await completeLogin(tokens);
          if (loggedIn) {
            window.history.replaceState({}, document.title, window.location.pathname);
          }
        }
      })
      .catch((error) => {
        console.error("Failed to complete Keycloak sign-in redirect:", error);
      })
      .finally(() => {
        if (!cancelled) {
          setProcessingAuthRedirect(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [completeLogin, isLoggedIn]);

  // Wait for auth state to be hydrated before redirecting
  if ((!isWeb && !_stateHydrated) || processingAuthRedirect) {
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
  if (isLoggedIn) {
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
