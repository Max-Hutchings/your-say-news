import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/**
 * The persistent privacy reassurance panel shown on every wizard step — calm green, a lock glyph,
 * and the promise that characteristics are only ever shown as anonymous aggregate.
 */
export function PrivacyNote({
    text = "Private. Only ever shown as anonymous aggregate — never tied to you.",
}: {
    text?: string;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    return (
        <View
            style={[
                styles.panel,
                { backgroundColor: e.privacyBg, borderColor: e.privacyBorder },
            ]}
        >
            <View style={[styles.lock, { borderColor: e.privacyIcon }]}>
                <View style={[styles.lockBody, { backgroundColor: e.privacyIcon }]} />
            </View>
            <Text style={[styles.text, { color: e.privacyText }]}>{text}</Text>
        </View>
    );
}

const styles = StyleSheet.create({
    panel: {
        flexDirection: "row",
        alignItems: "center",
        gap: 8,
        borderWidth: 1,
        borderRadius: 11,
        paddingVertical: 11,
        paddingHorizontal: 13,
    },
    lock: {
        width: 12,
        height: 14,
        alignItems: "center",
        justifyContent: "flex-end",
    },
    lockBody: {
        width: 10,
        height: 8,
        borderRadius: 2,
    },
    text: {
        flex: 1,
        fontFamily: EditorialFont.sans,
        fontSize: 12,
        lineHeight: 16,
    },
});
