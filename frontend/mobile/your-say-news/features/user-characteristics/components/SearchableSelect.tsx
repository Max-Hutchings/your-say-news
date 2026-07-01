import React, { useMemo, useState } from "react";
import {
    View,
    Text,
    Pressable,
    Modal,
    TextInput,
    ScrollView,
    StyleSheet,
} from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { Option } from "../types";
import { Eyebrow } from "@/components/ui";

/**
 * Searchable long-list select (design handoff): a labelled trigger opens a modal with a teal-ringed
 * search field, a live-filtered list with a checked selected row, and an "N OF M MATCHES" caption.
 * Used for the 195-country and other long enum lists.
 */
export function SearchableSelect({
    label,
    placeholder = "Select an option",
    options,
    selected,
    onSelect,
}: {
    label: string;
    placeholder?: string;
    options: Option[];
    selected: string | null;
    onSelect: (value: string) => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");

    const selectedLabel = selected ? options.find((o) => o.value === selected)?.label : null;

    const filtered = useMemo(() => {
        const q = query.trim().toLowerCase();
        if (!q) return options;
        return options.filter((o) => o.label.toLowerCase().includes(q));
    }, [options, query]);

    return (
        <View>
            <Eyebrow text={label} style={{ marginBottom: 8 }} />
            <Pressable
                onPress={() => setOpen(true)}
                style={[styles.trigger, { backgroundColor: e.surface, borderColor: e.border }]}
            >
                <Text
                    numberOfLines={1}
                    style={[styles.triggerText, { color: selectedLabel ? e.ink : e.muted }]}
                >
                    {selectedLabel ?? placeholder}
                </Text>
                <Text style={[styles.chevron, { color: e.muted }]}>⌄</Text>
            </Pressable>

            <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
                <Pressable
                    style={[styles.backdrop, { backgroundColor: "rgba(12,10,8,0.55)" }]}
                    onPress={() => setOpen(false)}
                >
                    <Pressable
                        style={[styles.sheet, { backgroundColor: e.bg, borderColor: e.border }]}
                        onPress={(ev) => ev.stopPropagation()}
                    >
                        <Eyebrow text={label} style={{ marginBottom: 10 }} />
                        <View style={[styles.searchBox, { backgroundColor: e.surface, borderColor: e.focus }]}>
                            <Text style={[styles.searchIcon, { color: e.muted }]}>⌕</Text>
                            <TextInput
                                value={query}
                                onChangeText={setQuery}
                                placeholder="Search…"
                                placeholderTextColor={e.muted}
                                autoFocus
                                style={[styles.searchInput, { color: e.ink }]}
                            />
                        </View>

                        <ScrollView style={styles.list} keyboardShouldPersistTaps="handled">
                            {filtered.map((opt) => {
                                const isSelected = selected === opt.value;
                                return (
                                    <Pressable
                                        key={opt.value}
                                        onPress={() => {
                                            onSelect(opt.value);
                                            setOpen(false);
                                            setQuery("");
                                        }}
                                        style={[
                                            styles.row,
                                            { borderBottomColor: e.border },
                                            isSelected && { backgroundColor: isDark ? "#22332B" : e.privacyBg },
                                        ]}
                                    >
                                        <Text
                                            style={[
                                                styles.rowText,
                                                { color: isSelected ? e.ink : e.secondary },
                                            ]}
                                        >
                                            {opt.label}
                                        </Text>
                                        {isSelected && (
                                            <Text style={[styles.check, { color: e.teal }]}>✓</Text>
                                        )}
                                    </Pressable>
                                );
                            })}
                        </ScrollView>

                        <Text style={[styles.matches, { color: e.muted }]}>
                            {filtered.length} OF {options.length} MATCHES
                        </Text>
                    </Pressable>
                </Pressable>
            </Modal>
        </View>
    );
}

const styles = StyleSheet.create({
    trigger: {
        height: 52,
        borderWidth: 1.5,
        borderRadius: 13,
        paddingHorizontal: 16,
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
    },
    triggerText: {
        flex: 1,
        fontFamily: EditorialFont.sansSemiBold,
        fontWeight: "600",
        fontSize: 15,
    },
    chevron: {
        fontSize: 16,
        marginLeft: 8,
    },
    backdrop: {
        flex: 1,
        justifyContent: "center",
        paddingHorizontal: 24,
    },
    sheet: {
        borderRadius: 18,
        borderWidth: 1,
        padding: 18,
        maxHeight: "75%",
    },
    searchBox: {
        flexDirection: "row",
        alignItems: "center",
        gap: 10,
        height: 52,
        borderWidth: 1.5,
        borderRadius: 13,
        paddingHorizontal: 15,
    },
    searchIcon: {
        fontSize: 18,
    },
    searchInput: {
        flex: 1,
        fontFamily: EditorialFont.sansSemiBold,
        fontWeight: "600",
        fontSize: 15,
    },
    list: {
        marginTop: 10,
    },
    row: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        paddingVertical: 13,
        paddingHorizontal: 6,
        borderBottomWidth: 1,
    },
    rowText: {
        fontFamily: EditorialFont.sansMedium,
        fontWeight: "500",
        fontSize: 14,
    },
    check: {
        fontSize: 15,
        fontWeight: "700",
    },
    matches: {
        fontFamily: EditorialFont.mono,
        fontSize: 10,
        letterSpacing: 0.6,
        marginTop: 12,
    },
});
