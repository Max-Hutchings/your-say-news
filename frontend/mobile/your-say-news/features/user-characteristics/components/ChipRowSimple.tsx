import React from "react";
import { View, StyleSheet } from "react-native";
import { Spacing } from "@/constants/theme";
import { SelectableChip } from "./SelectableChip";

/** A wrapping row of single-select chips for plain string options (age, gender, …). */
export function ChipRowSimple({
    options,
    selected,
    onSelect,
}: {
    options: string[];
    selected: string | null;
    onSelect: (value: string) => void;
}) {
    return (
        <View style={styles.wrap}>
            {options.map((label) => (
                <SelectableChip
                    key={label}
                    label={label}
                    selected={selected === label}
                    onPress={() => onSelect(label)}
                />
            ))}
        </View>
    );
}

const styles = StyleSheet.create({
    wrap: {
        flexDirection: "row",
        flexWrap: "wrap",
        marginTop: Spacing.xs,
    },
});
