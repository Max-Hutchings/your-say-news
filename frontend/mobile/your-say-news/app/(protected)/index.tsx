/**
 * Home Screen - News Feed
 * 
 * Main feed screen with themed styling
 */

import { View, StyleSheet, ScrollView } from "react-native";
import { useRouter } from "expo-router";
import { useAuthStore } from "@/features/auth";
import { ThemedView } from "@/components/themed-view";
import { ThemedText } from "@/components/themed-text";
import { Button, Card } from "@/components/ui";
import {
  useTheme,
  Spacing,
  BorderRadius,
  Shadows,
} from "@/constants/theme";

export default function Home() {
  const router = useRouter();
  const { hasOnboarded } = useAuthStore();
  const { colors } = useTheme();

  const handleVote = (vote: "agree" | "disagree"): void => {
    if (!hasOnboarded) {
      router.push("/usercharacteristics");
      return;
    }
    console.log("Submitting vote:", vote);
  };

  return (
    <ThemedView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <ThemedText variant="h3">Neutral News Feed</ThemedText>
        <ThemedText variant="bodySmall" color="secondary">
          Swipe through and share your perspective
        </ThemedText>
      </View>

      <ScrollView 
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* News Card */}
        <Card variant="elevated" padding="none" style={styles.newsCard}>
          {/* Media Placeholder */}
          <View 
            style={[
              styles.mediaPlaceholder, 
              { backgroundColor: colors.surface.tertiary }
            ]}
          >
            <ThemedText variant="h4" color="tertiary">
              📰
            </ThemedText>
            <ThemedText variant="labelMedium" color="tertiary">
              News item / video placeholder
            </ThemedText>
          </View>

          {/* Card Content */}
          <View style={styles.cardContent}>
            <ThemedText variant="overline" color="tertiary" style={styles.category}>
              Breaking News
            </ThemedText>
            
            <ThemedText variant="h4" style={styles.headline}>
              Headline placeholder for the news story
            </ThemedText>
            
            <ThemedText variant="bodySmall" color="secondary" style={styles.excerpt}>
              A brief excerpt or summary of the news article would appear here, 
              giving readers context before they vote.
            </ThemedText>

            {/* Vote Buttons */}
            <View style={styles.voteContainer}>
              <ThemedText variant="labelSmall" color="tertiary" style={styles.voteLabel}>
                {"What's your take?"}
              </ThemedText>
              
              <View style={styles.voteButtons}>
                <Button
                  variant="outline"
                  size="lg"
                  onPress={() => handleVote("agree")}
                  style={[styles.voteButton, { borderColor: colors.status.success }]}
                  textStyle={{ color: colors.status.success }}
                >
                  👍 I Agree
                </Button>
                
                <Button
                  variant="outline"
                  size="lg"
                  onPress={() => handleVote("disagree")}
                  style={[styles.voteButton, { borderColor: colors.status.error }]}
                  textStyle={{ color: colors.status.error }}
                >
                  👎 I Disagree
                </Button>
              </View>
            </View>
          </View>
        </Card>

        {/* More cards would go here */}
        <View style={styles.emptyState}>
          <ThemedText variant="bodyMedium" color="tertiary" style={styles.emptyText}>
            More stories coming soon...
          </ThemedText>
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: Spacing.base,
    paddingTop: Spacing.xl,
    paddingBottom: Spacing.base,
    gap: Spacing.xs,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: Spacing.base,
    gap: Spacing.lg,
  },
  newsCard: {
    overflow: "hidden",
    ...Shadows.md,
  },
  mediaPlaceholder: {
    height: 200,
    alignItems: "center",
    justifyContent: "center",
    gap: Spacing.sm,
  },
  cardContent: {
    padding: Spacing.base,
    gap: Spacing.sm,
  },
  category: {
    marginBottom: Spacing.xs,
  },
  headline: {
    marginBottom: Spacing.xs,
  },
  excerpt: {
    lineHeight: 22,
  },
  voteContainer: {
    marginTop: Spacing.lg,
    paddingTop: Spacing.base,
    borderTopWidth: 1,
    borderTopColor: "rgba(0,0,0,0.05)",
    gap: Spacing.md,
  },
  voteLabel: {
    textAlign: "center",
    textTransform: "uppercase",
    letterSpacing: 1,
  },
  voteButtons: {
    flexDirection: "row",
    gap: Spacing.md,
  },
  voteButton: {
    flex: 1,
    borderRadius: BorderRadius.lg,
  },
  emptyState: {
    paddingVertical: Spacing["3xl"],
    alignItems: "center",
  },
  emptyText: {
    textAlign: "center",
  },
});
