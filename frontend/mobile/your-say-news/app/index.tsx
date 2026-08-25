/**
 * Splash/Loading Screen
 * 
 * Initial loading screen with theme-aware styling
 */

import { useEffect, useState } from "react";
import { Platform, View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { Redirect } from "expo-router";
import {
  completeKeycloakWebRedirectFromUrl,
  useAuthStore,
  type SessionRestoreResult,
} from "@/features/auth";
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
  const { completeLogin, restoreSession, isLoggedIn, _stateHydrated } = useAuthStore();
  // Captured once: completing the exchange strips the params from the URL, and the render gate
  // below must not flip the moment that happens.
  const [hadAuthRedirect] = useState(hasAuthRedirectParams);
  const [processingAuthRedirect, setProcessingAuthRedirect] = useState(hasAuthRedirectParams);
  const [sessionResult, setSessionResult] = useState<SessionRestoreResult | null>(null);

  // A sign-in redirect always wins. The user has just authenticated as somebody, and that identity
  // replaces whatever was in storage outright. Deferring to a persisted session here is what made
  // every sign-in land back on the stored user no matter who was entered at Keycloak.
  useEffect(() => {
    if (typeof window === "undefined" || !hadAuthRedirect) {
      return;
    }

    let cancelled = false;
    completeKeycloakWebRedirectFromUrl(window.location.href)
      .then(async (tokens) => {
        if (cancelled || !tokens) {
          return;
        }
        const loggedIn = await completeLogin(tokens);
        if (loggedIn) {
          window.history.replaceState({}, document.title, window.location.pathname);
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
  }, [completeLogin, hadAuthRedirect]);

  // No sign-in in flight: whatever was restored from storage is only honoured once the server
  // confirms it still accepts those credentials. restoreSession wipes a dead session itself, so it
  // can never be served to the next person to open the app and nobody has to clear site data by hand.
  useEffect(() => {
    if (hadAuthRedirect || !_stateHydrated) {
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
  }, [restoreSession, hadAuthRedirect, _stateHydrated]);

  const checkingSession = hadAuthRedirect ? processingAuthRedirect : sessionResult === null;

  // Only a session the server vouched for gets into the app. A restored session we could not verify
  // is deliberately not admitted, even though its credentials are kept for a later attempt.
  const admitted = hadAuthRedirect ? isLoggedIn : sessionResult === "signed-in";

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
