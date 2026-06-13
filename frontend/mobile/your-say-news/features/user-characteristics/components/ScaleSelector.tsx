import React from "react";
import { View, Text, TouchableOpacity, StyleSheet } from "react-native";
import { ThemedText } from "@/components/themed-text";
import { useTheme, Spacing, BorderRadius, BorderWidth, Typography } from "@/constants/theme";

type ScaleSelectorProps = {
    question: string;
    subtitle?: string;
    min?: number;
    max?: number;
    value: number | null;
    onChange: (value: number) => void;
    leftLabel?: string;
    rightLabel?: string;
};

/** A 0–10 Typeform-style numeric scale selector. */
export function ScaleSelector({
    question,
    subtitle,
    min = 0,
    max = 10,
    value,
    onChange,
    leftLabel,
    rightLabel,
}: ScaleSelectorProps) {
    const { colors } = useTheme();

    const numbers: number[] = [];
    for (let i = min; i <= max; i++) {
        numbers.push(i);
    }

    return (
        <View style={styles.container}>
            <ThemedText variant="h4">{question}</ThemedText>
            {subtitle ? (
                <ThemedText variant="bodySmall" color="tertiary" style={styles.subtitle}>
                    {subtitle}
                </ThemedText>
            ) : null}

            <View style={styles.row}>
                {numbers.map((n) => {
                    const selected = value === n;
                    return (
                        <TouchableOpacity
                            key={n}
                            onPress={() => onChange(n)}
                            style={[
                                styles.box,
                                {
                                    borderColor: selected
                                        ? colors.interactive.primary
                                        : colors.border.secondary,
                                    backgroundColor: selected
                                        ? colors.interactive.primary
                                        : colors.surface.secondary,
                                },
                            ]}
                        >
                            <Text
                                style={[
                                    styles.boxText,
                                    { color: selected ? colors.text.inverse : colors.text.secondary },
                                ]}
                            >
                                {n}
                            </Text>
                        </TouchableOpacity>
                    );
                })}
            </View>

            <View style={styles.labelsRow}>
                <ThemedText variant="caption" color="tertiary">
                    {leftLabel || ""}
                </ThemedText>
                <ThemedText variant="caption" color="tertiary">
                    {rightLabel || ""}
                </ThemedText>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        marginTop: Spacing.sm,
    },
    subtitle: {
        marginBottom: Spacing.base,
    },
    row: {
        flexDirection: "row",
        flexWrap: "wrap",
        gap: Spacing.sm,
    },
    box: {
        width: 40,
        height: 48,
        borderRadius: BorderRadius.lg,
        borderWidth: BorderWidth.thin,
        alignItems: "center",
        justifyContent: "center",
    },
    boxText: {
        ...Typography.labelLarge,
    },
    labelsRow: {
        flexDirection: "row",
        justifyContent: "space-between",
        marginTop: Spacing.sm,
    },
});
