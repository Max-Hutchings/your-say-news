/**
 * Sign In Screen
 * 
 * Beautiful, themed sign-in experience
 */

import { View, StyleSheet } from "react-native";
import { useAuthStore } from "@/features/auth";
import { LinearGradient } from "expo-linear-gradient";
import { ThemedText } from "@/components/themed-text";
import { Button } from "@/components/ui";
import {
  useTheme,
  Spacing,
  Typography,
  BrandColors,
  NeutralColors,
} from "@/constants/theme";

export default function SignInScreen() {
  const { login } = useAuthStore();
  const { colors, isDark } = useTheme();

  // Gradient colors based on theme
  const gradientColors = isDark
    ? [NeutralColors.slate[950], NeutralColors.slate[900], BrandColors.primary[950]]
    : [BrandColors.primary[50], NeutralColors.white, BrandColors.primary[100]];

  return (
    <LinearGradient colors={gradientColors} style={styles.container}>
      {/* Logo/Brand Section */}
      <View style={styles.brandSection}>
        <View style={[styles.logoContainer, { backgroundColor: colors.brand.primary }]}>
          <ThemedText
            variant="displaySmall"
            style={[styles.logoText, { color: NeutralColors.white }]}
          >
            YS
          </ThemedText>
        </View>

        <ThemedText variant="displaySmall" style={styles.title}>
          YourSay News
        </ThemedText>

        <ThemedText variant="bodyLarge" color="secondary" style={styles.tagline}>
          Neutral, people-powered news
        </ThemedText>
      </View>

      {/* Features Section */}
      <View style={styles.featuresSection}>
        <FeatureItem
          emoji="🎯"
          text="Unbiased news coverage"
          colors={colors}
        />
        <FeatureItem
          emoji="👥"
          text="Community-driven insights"
          colors={colors}
        />
        <FeatureItem
          emoji="🔒"
          text="Your privacy protected"
          colors={colors}
        />
      </View>

      {/* Sign In Section */}
      <View style={styles.authSection}>
        <Button
          onPress={login}
          size="lg"
          fullWidth
          style={styles.signInButton}
        >
          Sign In to Continue
        </Button>

        <ThemedText variant="caption" color="tertiary" style={styles.termsText}>
          By signing in, you agree to our Terms of Service and Privacy Policy
        </ThemedText>
      </View>
    </LinearGradient>
  );
}

// Feature item component
function FeatureItem({
  emoji,
  text,
  colors,
}: {
  emoji: string;
  text: string;
  colors: any;
}) {
  return (
    <View style={styles.featureItem}>
      <View
        style={[
          styles.featureIcon,
          { backgroundColor: colors.surface.secondary },
        ]}
      >
        <ThemedText variant="h4">{emoji}</ThemedText>
      </View>
      <ThemedText variant="bodyMedium" color="secondary">
        {text}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: Spacing.xl,
    paddingTop: Spacing["6xl"],
    paddingBottom: Spacing["3xl"],
  },
  brandSection: {
    alignItems: "center",
    marginBottom: Spacing["4xl"],
  },
  logoContainer: {
    width: 80,
    height: 80,
    borderRadius: 20,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: Spacing.lg,
  },
  logoText: {
    fontWeight: "800",
  },
  title: {
    textAlign: "center",
    marginBottom: Spacing.sm,
  },
  tagline: {
    textAlign: "center",
  },
  featuresSection: {
    flex: 1,
    justifyContent: "center",
    gap: Spacing.lg,
  },
  featureItem: {
    flexDirection: "row",
    alignItems: "center",
    gap: Spacing.base,
  },
  featureIcon: {
    width: 48,
    height: 48,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  authSection: {
    gap: Spacing.base,
  },
  signInButton: {
    borderRadius: 16,
  },
  termsText: {
    textAlign: "center",
    paddingHorizontal: Spacing.xl,
  },
});
