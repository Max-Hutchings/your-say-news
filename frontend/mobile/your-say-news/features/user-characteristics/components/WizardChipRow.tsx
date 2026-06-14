import React from "react";
import { View, StyleSheet } from "react-native";
import type { Option } from "../types";
import { WizardChip } from "./WizardChip";

/** A wrapping row of single-select chips over enum-backed options. */
export function WizardChipRow({
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
                <WizardChip
                    key={opt.value}
                    label={opt.label}
                    selected={selected === opt.value}
                    onPress={() => onSelect(opt.value)}
                />
            ))}
        </View>
    );
}

/** A wrapping row of multi-select chips; `selected` is the set of chosen values. */
export function WizardChipMultiRow({
    options,
    selected,
    onToggle,
}: {
    options: Option[];
    selected: string[];
    onToggle: (value: string) => void;
}) {
    return (
        <View style={styles.wrap}>
            {options.map((opt) => (
                <WizardChip
                    key={opt.value}
                    label={opt.label}
                    selected={selected.includes(opt.value)}
                    onPress={() => onToggle(opt.value)}
                />
            ))}
        </View>
    );
}

const styles = StyleSheet.create({
    wrap: {
        flexDirection: "row",
        flexWrap: "wrap",
        gap: 9,
    },
});
