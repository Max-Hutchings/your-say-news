/**
 * About Your Say News
 *
 * Explains what the product does. Thin route — presentation only.
 */

import { ScrollView, StyleSheet } from "react-native";
import { ThemedView } from "@/components/themed-view";
import { ThemedText } from "@/components/themed-text";
import { Spacing } from "@/constants/theme";

export default function AboutYourSayNews() {
    return (
        <ThemedView style={styles.container}>
            <ScrollView contentContainerStyle={styles.content}>
                <ThemedText variant="h2">All you need to know about Your Say News</ThemedText>

                <ThemedText variant="bodyMedium" color="secondary">
                    Your Say News is where people and publishers share stories, and everyone
                    gets a vote.
                </ThemedText>

                <ThemedText variant="bodyMedium" color="secondary">
                    We feed the results back to you as aggregated, anonymised sentiment —
                    showing how different kinds of people feel about a topic, broken down by
                    characteristics like age, region and income, never by individual identity.
                </ThemedText>

                <ThemedText variant="bodyMedium" color="secondary">
                    Your personal details are kept separate from your votes and are never shown
                    publicly.
                </ThemedText>
            </ScrollView>
        </ThemedView>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    content: {
        padding: Spacing.base,
        gap: Spacing.base,
    },
});
