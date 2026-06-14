import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

const MIN_BAR = 16;
const MAX_BAR = 62;

/** Word label for a 0–10 news-following score. */
export function newsWord(value: number): string {
    if (value <= 2) return "Headlines only";
    if (value <= 4) return "Occasional";
    if (value <= 6) return "Daily reader";
    if (value <= 8) return "Well-read";
    return "News junkie";
}

/**
 * The 0–10 "how often do you follow the news?" scale: a row of 11 rising bars (filled up to the
 * selected value, the selected bar ringed in lime) with a big serif number and a word label.
 */
export function WizardScale({
    value,
    onChange,
}: {
    value: number | null;
    onChange: (value: number) => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const current = value ?? 0;

    return (
        <View>
            <View style={styles.headerRow}>
                <Text style={[styles.number, { color: e.ink }]}>{value === null ? "–" : value}</Text>
                <Text style={[styles.word, { color: e.teal }]}>
                    {value === null ? "Tap to rate" : newsWord(value)}
                </Text>
            </View>

            <View style={styles.bars}>
                {Array.from({ length: 11 }).map((_, i) => {
                    const height = MIN_BAR + (i / 10) * (MAX_BAR - MIN_BAR);
                    const filled = value !== null && i <= current;
                    const isSelected = value !== null && i === current;
                    return (
                        <Pressable
                            key={i}
                            onPress={() => onChange(i)}
                            accessibilityRole="adjustable"
                            accessibilityLabel={`News following ${i} of 10`}
                            style={styles.barHit}
                        >
                            <View
                                style={[
                                    styles.bar,
                                    {
                                        height,
                                        backgroundColor: filled ? e.ink : e.track,
                                        borderColor: isSelected ? e.lime : "transparent",
                                        borderWidth: isSelected ? 2 : 0,
                                    },
                                ]}
                            />
                        </Pressable>
                    );
                })}
            </View>

            <View style={styles.captions}>
                <Text style={[styles.caption, { color: e.muted }]}>0 · HEADLINES ONLY</Text>
                <Text style={[styles.caption, { color: e.muted }]}>10 · ALL DAY</Text>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    headerRow: {
        flexDirection: "row",
        alignItems: "baseline",
        gap: 10,
        marginBottom: 18,
    },
    number: {
        fontFamily: EditorialFont.serif,
        fontSize: 52,
        lineHeight: 56,
    },
    word: {
        fontFamily: EditorialFont.sansBold,
        fontWeight: "700",
        fontSize: 16,
    },
    bars: {
        flexDirection: "row",
        alignItems: "flex-end",
        gap: 5,
        height: MAX_BAR,
    },
    barHit: {
        flex: 1,
        justifyContent: "flex-end",
    },
    bar: {
        width: "100%",
        borderRadius: 4,
    },
    captions: {
        flexDirection: "row",
        justifyContent: "space-between",
        marginTop: 10,
    },
    caption: {
        fontFamily: EditorialFont.mono,
        fontSize: 10,
    },
});
