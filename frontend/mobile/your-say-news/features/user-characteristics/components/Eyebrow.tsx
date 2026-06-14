import React from "react";
import { Text, StyleSheet, TextStyle } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/** A mono, letter-spaced, uppercase field label / eyebrow (e.g. "COUNTRY OR REGION"). */
export function Eyebrow({ text, style }: { text: string; style?: TextStyle }) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    return <Text style={[styles.eyebrow, { color: e.muted }, style]}>{text.toUpperCase()}</Text>;
}

const styles = StyleSheet.create({
    eyebrow: {
        fontFamily: EditorialFont.mono,
        fontSize: 10,
        letterSpacing: 1.4,
    },
});
