
import React from "react";
import { TouchableOpacity, Text, StyleSheet } from "react-native";

type Props = {
    label: string;
    selected: boolean;
    onPress: () => void;
};

export function SelectableChip({ label, selected, onPress }: Props) {
    return (
        <TouchableOpacity
            onPress={onPress}
            style={[styles.chip, selected && styles.chipSelected]}
        >
            <Text style={[styles.chipText, selected && styles.chipTextSelected]}>
                {label}
            </Text>
        </TouchableOpacity>
    );
}

const styles = StyleSheet.create({
    chip: {
        paddingHorizontal: 12,
        paddingVertical: 8,
        borderRadius: 20,
        borderWidth: 1,
        borderColor: "#ccc",
        marginRight: 8,
        marginBottom: 8,
    },
    chipSelected: {
        backgroundColor: "#000",
        borderColor: "#000",
    },
    chipText: {
        fontSize: 14,
        color: "#333",
    },
    chipTextSelected: {
        color: "#fff",
    },
});
