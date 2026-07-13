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
 * Searchable long-list MULTI select — the multi-select sibling of {@link SearchableSelect}, used for
 * nationality where a user may hold more than one. Tapping a row toggles it and keeps the modal open;
 * the trigger shows the chosen labels (or a count) so selections are visible without opening it.
 */
export function SearchableMultiSelect({
    label,
    placeholder = "Select all that apply",
    options,
    selected,
    onToggle,
}: {
    label: string;
    placeholder?: string;
    options: Option[];
    selected: string[];
    onToggle: (value: string) => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState("");

    const selectedLabels = options
        .filter((o) => selected.includes(o.value))
        .map((o) => o.label)
        .join(", ");

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
                    style={[styles.triggerText, { color: selectedLabels ? e.ink : e.muted }]}
                >
                    {selectedLabels || placeholder}
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
                                const isSelected = selected.includes(opt.value);
                                return (
                                    <Pressable
                                        key={opt.value}
                                        onPress={() => onToggle(opt.value)}
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

                        <View style={styles.footer}>
                            <Text style={[styles.matches, { color: e.muted }]}>
                                {selected.length} SELECTED · {filtered.length} OF {options.length}
                            </Text>
                            <Pressable onPress={() => setOpen(false)}>
                                <Text style={[styles.done, { color: e.teal }]}>DONE</Text>
                            </Pressable>
                        </View>
                    </Pressable>
                </Pressable>
            </Modal>
        </View>
    );
}

const styles = StyleSheet.create({
    trigger: {
        minHeight: 52,
        borderWidth: 1.5,
        borderRadius: 13,
        paddingHorizontal: 16,
        paddingVertical: 8,
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
    chevron: { fontSize: 16, marginLeft: 8 },
    backdrop: { flex: 1, justifyContent: "center", paddingHorizontal: 24 },
    sheet: { borderRadius: 18, borderWidth: 1, padding: 18, maxHeight: "75%" },
    searchBox: {
        flexDirection: "row",
        alignItems: "center",
        gap: 10,
        height: 52,
        borderWidth: 1.5,
        borderRadius: 13,
        paddingHorizontal: 15,
    },
    searchIcon: { fontSize: 18 },
    searchInput: {
        flex: 1,
        fontFamily: EditorialFont.sansSemiBold,
        fontWeight: "600",
        fontSize: 15,
    },
    list: { marginTop: 10 },
    row: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        paddingVertical: 13,
        paddingHorizontal: 6,
        borderBottomWidth: 1,
    },
    rowText: { fontFamily: EditorialFont.sansMedium, fontWeight: "500", fontSize: 14 },
    check: { fontSize: 15, fontWeight: "700" },
    footer: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "space-between",
        marginTop: 12,
    },
    matches: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 0.6 },
    done: { fontFamily: EditorialFont.monoSemiBold, fontSize: 12, fontWeight: "700", letterSpacing: 0.6 },
});
