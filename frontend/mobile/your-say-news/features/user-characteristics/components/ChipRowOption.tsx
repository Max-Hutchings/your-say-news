import React from "react";
import { View, StyleSheet } from "react-native";
import { Spacing } from "@/constants/theme";
import type { Option } from "../types";
import { SelectableChip } from "./SelectableChip";

/** A wrapping row of single-select chips for enum-backed `Option[]` lists. */
export function ChipRowOption({
    options,
    selected,
    onSelect,
}: {
    options: Option[];
    selected: string | null;
    onSelect: (value: string) => void;
}) {
    return (
        <View style={styles.wrap}>
            {options.map((opt) => (
                <SelectableChip
                    key={opt.value}
                    label={opt.label}
                    selected={selected === opt.value}
                    onPress={() => onSelect(opt.value)}
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
