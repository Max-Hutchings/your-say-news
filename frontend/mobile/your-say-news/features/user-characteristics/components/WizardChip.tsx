import React from "react";
import { Pressable, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * Editorial selectable chip (design handoff): 1.5px border, radius 11. Unselected = paper surface
 * with ink text; selected inverts to an ink fill with paper text.
 */
export function WizardChip({
    label,
    selected,
    onPress,
}: {
    label: string;
    selected: boolean;
    onPress: () => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);

    return (
        <Pressable
            onPress={onPress}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            style={[
                styles.chip,
                {
                    backgroundColor: selected ? e.ink : e.chipBg,
                    borderColor: selected ? e.ink : e.chipBorder,
                },
            ]}
        >
            <Text style={[styles.label, { color: selected ? e.bg : e.chipText }]}>{label}</Text>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    chip: {
        paddingVertical: 11,
        paddingHorizontal: 15,
        borderRadius: 11,
        borderWidth: 1.5,
    },
    label: {
        fontFamily: EditorialFont.sansSemiBold,
        fontWeight: "600",
        fontSize: 14,
    },
});
