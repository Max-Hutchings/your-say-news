import React from "react";
import { StyleSheet } from "react-native";
import { ThemedText } from "@/components/themed-text";
import { Spacing } from "@/constants/theme";

/** Small form-field label used throughout the onboarding steps. */
export function FieldLabel({ text }: { text: string }) {
    return (
        <ThemedText variant="labelMedium" color="secondary" style={styles.label}>
            {text}
        </ThemedText>
    );
}

const styles = StyleSheet.create({
    label: {
        marginBottom: Spacing.xs,
        marginTop: Spacing.sm,
    },
});
