import React, { useRef } from "react";
import { View, Text, PanResponder, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

const HANDLE = 28;
const TRACK = 12;

function clamp(v: number) {
    return Math.max(0, Math.min(100, Math.round(v)));
}

/**
 * Split-bar slider for mainstream vs social media news consumption.
 * `value` is 0–100: percentage of mainstream news (social = 100 - value).
 */
export function NewsSourceSlider({
    value,
    onChange,
}: {
    value: number;
    onChange: (v: number) => void;
}) {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);

    const trackWidth = useRef(0);
    const startValue = useRef(value);
    const startTouchX = useRef(0);
    const valueRef = useRef(value);
    valueRef.current = value;

    const panResponder = useRef(
        PanResponder.create({
            onStartShouldSetPanResponder: () => true,
            onMoveShouldSetPanResponder: () => true,
            onPanResponderGrant: (evt) => {
                if (trackWidth.current > 0) {
                    const pct = clamp((evt.nativeEvent.locationX / trackWidth.current) * 100);
                    onChange(pct);
                    startValue.current = pct;
                } else {
                    startValue.current = valueRef.current;
                }
                startTouchX.current = evt.nativeEvent.pageX;
            },
            onPanResponderMove: (evt) => {
                if (trackWidth.current === 0) return;
                const delta = ((evt.nativeEvent.pageX - startTouchX.current) / trackWidth.current) * 100;
                onChange(clamp(startValue.current + delta));
            },
        })
    ).current;

    const mainstream = clamp(value);
    const social = 100 - mainstream;
    const inkColor = isDark ? e.lime : e.ink;

    return (
        <View>
            <View style={styles.labels}>
                <View>
                    <Text style={[styles.sourceLabel, { color: e.muted }]}>MAINSTREAM</Text>
                    <Text style={[styles.pct, { color: inkColor }]}>{mainstream}%</Text>
                </View>
                <View style={{ alignItems: "flex-end" }}>
                    <Text style={[styles.sourceLabel, { color: e.muted }]}>SOCIAL MEDIA</Text>
                    <Text style={[styles.pct, { color: e.teal }]}>{social}%</Text>
                </View>
            </View>

            <View
                style={styles.outerTrack}
                onLayout={(ev) => {
                    trackWidth.current = ev.nativeEvent.layout.width;
                }}
                {...panResponder.panHandlers}
            >
                {/* Track background (social color) */}
                <View
                    style={[
                        StyleSheet.absoluteFill,
                        styles.track,
                        { backgroundColor: e.teal, overflow: "hidden" },
                    ]}
                >
                    {/* Mainstream fill */}
                    <View
                        style={[
                            StyleSheet.absoluteFill,
                            { right: `${social}%`, backgroundColor: inkColor },
                        ]}
                    />
                </View>

                {/* Handle */}
                <View
                    pointerEvents="none"
                    style={[
                        styles.handle,
                        {
                            left: `${mainstream}%` as any,
                            backgroundColor: e.bg,
                            borderColor: inkColor,
                        },
                    ]}
                />
            </View>

            <View style={styles.captions}>
                <Text style={[styles.caption, { color: e.muted }]}>0% · ALL SOCIAL</Text>
                <Text style={[styles.caption, { color: e.muted }]}>100% · ALL MAINSTREAM</Text>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    labels: {
        flexDirection: "row",
        justifyContent: "space-between",
        marginBottom: 20,
    },
    sourceLabel: {
        fontFamily: EditorialFont.mono,
        fontSize: 10,
        letterSpacing: 0.5,
        marginBottom: 4,
    },
    pct: {
        fontFamily: EditorialFont.serif,
        fontSize: 38,
        lineHeight: 42,
    },
    outerTrack: {
        height: HANDLE,
        justifyContent: "center",
    },
    track: {
        top: (HANDLE - TRACK) / 2,
        bottom: (HANDLE - TRACK) / 2,
        borderRadius: TRACK / 2,
    },
    handle: {
        position: "absolute",
        width: HANDLE,
        height: HANDLE,
        borderRadius: HANDLE / 2,
        borderWidth: 3,
        marginLeft: -(HANDLE / 2),
        shadowColor: "#000",
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.18,
        shadowRadius: 4,
        elevation: 4,
    },
    captions: {
        flexDirection: "row",
        justifyContent: "space-between",
        marginTop: 12,
    },
    caption: {
        fontFamily: EditorialFont.mono,
        fontSize: 10,
    },
});
