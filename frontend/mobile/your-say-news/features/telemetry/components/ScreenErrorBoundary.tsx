import { useEffect } from "react";
import { Pressable, StyleSheet, Text, useColorScheme, View } from "react-native";
import { SplashScreen, type ErrorBoundaryProps } from "expo-router";
import { EditorialFont, getEditorial } from "@/constants/theme";
import { reportError } from "../services/telemetry";

/**
 * expo-router error boundary: reports the render crash as a fault on the screen it happened on, then
 * offers a retry instead of a blank app. It replaces the root layout, so ThemeProvider is not
 * mounted here and the system colour scheme is read directly.
 */
export function ScreenErrorBoundary({ error, retry }: ErrorBoundaryProps) {
    const e = getEditorial(useColorScheme() === "dark");

    useEffect(() => {
        reportError(error);
        // A crash before fonts load would otherwise leave the native splash covering this screen.
        SplashScreen.hideAsync().catch(() => undefined);
    }, [error]);

    return (
        <View style={[styles.screen, { backgroundColor: e.bg }]}>
            <Text style={[styles.eyebrow, { color: e.muted }]}>SOMETHING WENT WRONG</Text>
            <Text style={[styles.title, { color: e.ink }]}>This page could not be shown.</Text>
            <Pressable
                accessibilityRole="button"
                onPress={() => void retry()}
                style={[styles.retry, { backgroundColor: e.lime }]}
            >
                <Text style={[styles.retryLabel, { color: e.onLime }]}>Try again</Text>
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    screen: { flex: 1, alignItems: "center", justifyContent: "center", paddingHorizontal: 26, gap: 14 },
    eyebrow: { fontFamily: EditorialFont.mono, fontSize: 11, letterSpacing: 1.6 },
    title: { fontFamily: EditorialFont.serif, fontSize: 24, textAlign: "center" },
    retry: { marginTop: 10, paddingHorizontal: 22, paddingVertical: 12, borderRadius: 999 },
    retryLabel: { fontFamily: EditorialFont.sansBold, fontSize: 15 },
});
