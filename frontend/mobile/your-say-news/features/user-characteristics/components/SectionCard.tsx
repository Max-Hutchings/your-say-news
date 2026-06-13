import React from "react";
import { View, StyleSheet } from "react-native";
import { ThemedText } from "@/components/themed-text";
import { useTheme, Spacing, BorderRadius, BorderWidth, Shadows } from "@/constants/theme";

/** A titled, elevated card grouping related onboarding fields. */
export function SectionCard({
    title,
    children,
}: {
    title: string;
    children: React.ReactNode;
}) {
    const { colors } = useTheme();

    return (
        <View
            style={[
                styles.card,
                { backgroundColor: colors.surface.primary, borderColor: colors.border.primary },
            ]}
        >
            <ThemedText variant="h4" style={styles.title}>
                {title}
            </ThemedText>
            {children}
        </View>
    );
}

const styles = StyleSheet.create({
    card: {
        marginTop: Spacing.lg,
        padding: Spacing.lg,
        borderRadius: BorderRadius["2xl"],
        borderWidth: BorderWidth.thin,
        ...Shadows.md,
    },
    title: {
        marginBottom: Spacing.sm,
    },
});
