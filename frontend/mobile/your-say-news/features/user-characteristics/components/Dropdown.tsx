import React from "react";
import { View, Text, Pressable, Modal, ScrollView, StyleSheet } from "react-native";
import { ThemedText } from "@/components/themed-text";
import {
    useTheme,
    Spacing,
    BorderRadius,
    BorderWidth,
    Typography,
    withOpacity,
} from "@/constants/theme";
import type { Option } from "../types";
import { FieldLabel } from "./FieldLabel";

type DropdownProps = {
    label: string;
    placeholder?: string;
    options: Option[];
    selected: string | null;
    onSelect: (value: string) => void;
};

/** A labelled trigger that opens a modal list for long enum-backed option sets. */
export function Dropdown({
    label,
    placeholder = "Select an option",
    options,
    selected,
    onSelect,
}: DropdownProps) {
    const { colors } = useTheme();
    const [open, setOpen] = React.useState(false);

    const selectedLabel = selected && options.find((o) => o.value === selected)?.label;

    return (
        <View style={styles.container}>
            <FieldLabel text={label} />
            <Pressable
                onPress={() => setOpen(true)}
                style={[
                    styles.trigger,
                    { borderColor: colors.input.border, backgroundColor: colors.input.background },
                ]}
            >
                <Text
                    style={[
                        styles.triggerText,
                        { color: selectedLabel ? colors.input.text : colors.input.placeholder },
                    ]}
                >
                    {selectedLabel || placeholder}
                </Text>
            </Pressable>

            <Modal
                visible={open}
                transparent
                animationType="fade"
                onRequestClose={() => setOpen(false)}
            >
                <Pressable
                    style={[styles.backdrop, { backgroundColor: colors.background.overlay }]}
                    onPress={() => setOpen(false)}
                >
                    <View style={[styles.sheet, { backgroundColor: colors.surface.elevated }]}>
                        <ThemedText variant="labelLarge" style={styles.sheetTitle}>
                            {label}
                        </ThemedText>
                        <ScrollView style={styles.sheetList}>
                            {options.map((opt) => {
                                const isSelected = selected === opt.value;
                                return (
                                    <Pressable
                                        key={opt.value}
                                        onPress={() => {
                                            onSelect(opt.value);
                                            setOpen(false);
                                        }}
                                        style={[
                                            styles.option,
                                            isSelected && {
                                                backgroundColor: withOpacity(colors.interactive.primary, 0.1),
                                            },
                                        ]}
                                    >
                                        <Text
                                            style={[
                                                styles.optionText,
                                                {
                                                    color: isSelected
                                                        ? colors.brand.primary
                                                        : colors.text.primary,
                                                    fontWeight: isSelected ? "600" : "400",
                                                },
                                            ]}
                                        >
                                            {opt.label}
                                        </Text>
                                    </Pressable>
                                );
                            })}
                        </ScrollView>
                    </View>
                </Pressable>
            </Modal>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        marginTop: Spacing.md,
    },
    trigger: {
        borderRadius: BorderRadius.lg,
        borderWidth: BorderWidth.thin,
        paddingHorizontal: Spacing.md,
        paddingVertical: Spacing.md,
        marginTop: Spacing.xs,
    },
    triggerText: {
        ...Typography.bodyMedium,
    },
    backdrop: {
        flex: 1,
        justifyContent: "center",
        paddingHorizontal: Spacing.xl,
    },
    sheet: {
        borderRadius: BorderRadius.xl,
        padding: Spacing.base,
        maxHeight: "80%",
    },
    sheetTitle: {
        marginBottom: Spacing.md,
    },
    sheetList: {
        maxHeight: 320,
    },
    option: {
        paddingVertical: Spacing.md,
        paddingHorizontal: Spacing.sm,
        borderRadius: BorderRadius.md,
        marginBottom: Spacing.xs,
    },
    optionText: {
        ...Typography.bodyMedium,
    },
});
