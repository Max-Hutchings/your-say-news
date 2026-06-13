import React from "react";
import { TouchableOpacity, Text, StyleSheet } from "react-native";
import { useTheme, Spacing, BorderRadius, BorderWidth, Typography } from "@/constants/theme";

type Props = {
    label: string;
    selected: boolean;
    onPress: () => void;
};

export function SelectableChip({ label, selected, onPress }: Props) {
    const { colors } = useTheme();

    return (
        <TouchableOpacity
            onPress={onPress}
            style={[
                styles.chip,
                {
                    borderColor: selected ? colors.interactive.primary : colors.border.secondary,
                    backgroundColor: selected ? colors.interactive.primary : "transparent",
                },
            ]}
        >
            <Text
                style={[
                    styles.chipText,
                    { color: selected ? colors.text.inverse : colors.text.secondary },
                ]}
            >
                {label}
            </Text>
        </TouchableOpacity>
    );
}

const styles = StyleSheet.create({
    chip: {
        paddingHorizontal: Spacing.md,
        paddingVertical: Spacing.sm,
        borderRadius: BorderRadius["2xl"],
        borderWidth: BorderWidth.thin,
        marginRight: Spacing.sm,
        marginBottom: Spacing.sm,
    },
    chipText: {
        ...Typography.labelMedium,
    },
});
