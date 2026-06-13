/**
 * Splash/Loading Screen
 * 
 * Initial loading screen with theme-aware styling
 */

import { View, ActivityIndicator, StyleSheet } from "react-native";
import { Redirect } from "expo-router";
import { useAuthStore } from "@/features/auth";
import { LinearGradient } from "expo-linear-gradient";
import { ThemedText } from "@/components/themed-text";
import {
  useTheme,
  Spacing,
  BrandColors,
  NeutralColors,
} from "@/constants/theme";

export default function SplashScreen() {
  const { isLoggedIn, _stateHydrated } = useAuthStore();
  const { isDark } = useTheme();

  // Wait for auth state to be hydrated before redirecting
  if (!_stateHydrated) {
    // Gradient colors based on theme
    const gradientColors = isDark
      ? [NeutralColors.slate[950], BrandColors.primary[950], NeutralColors.slate[900]]
      : [BrandColors.primary[500], BrandColors.primary[600], BrandColors.primary[700]];

    return (
      <LinearGradient colors={gradientColors} style={styles.container}>
        {/* Logo */}
        <View style={styles.logoContainer}>
          <ThemedText
            variant="displayLarge"
            style={[styles.logoText, { color: NeutralColors.white }]}
          >
            YS
          </ThemedText>
        </View>

        {/* Brand Text */}
        <ThemedText
          variant="displaySmall"
          style={[styles.title, { color: NeutralColors.white }]}
        >
          YourSay News
        </ThemedText>

        <ThemedText
          variant="bodyLarge"
          style={[styles.subtitle, { color: NeutralColors.slate[200] }]}
        >
          Neutral, people-powered news
        </ThemedText>

        {/* Loading Indicator */}
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={NeutralColors.white} />
          <ThemedText
            variant="labelMedium"
            style={[styles.loadingText, { color: NeutralColors.slate[300] }]}
          >
            Loading...
          </ThemedText>
        </View>
      </LinearGradient>
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
    paddingHorizontal: Spacing.xl,
  },
  logoContainer: {
    width: 120,
    height: 120,
    borderRadius: 32,
    backgroundColor: "rgba(255, 255, 255, 0.15)",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: Spacing["2xl"],
    // Glass effect border
    borderWidth: 1,
    borderColor: "rgba(255, 255, 255, 0.2)",
  },
  logoText: {
    fontWeight: "900",
    letterSpacing: -2,
  },
  title: {
    textAlign: "center",
    marginBottom: Spacing.sm,
    fontWeight: "700",
  },
  subtitle: {
    textAlign: "center",
    marginBottom: Spacing["4xl"],
  },
  loadingContainer: {
    alignItems: "center",
    gap: Spacing.md,
  },
  loadingText: {
    textTransform: "uppercase",
    letterSpacing: 2,
  },
});
