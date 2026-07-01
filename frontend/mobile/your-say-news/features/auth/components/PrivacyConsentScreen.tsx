import React, { useState } from "react";
import { View, Text, ScrollView, Pressable, StyleSheet, Alert } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";

import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { useAuthStore } from "../services/authContext";
import { recordConsent } from "../services/ConsentService";

const PROMISES: { kind: "do" | "never"; text: string }[] = [
    { kind: "do", text: "We collect characteristics in bands — age range, region, leaning — never exact details." },
    { kind: "do", text: "Results are only ever shown as an anonymous aggregate: how groups of people split." },
    { kind: "never", text: "Your name or email is never shown beside a vote, to anyone." },
    { kind: "never", text: "Your identity is never joined to your characteristics in anything we publish." },
];

/**
 * Privacy promise + explicit consent — shown once after first sign-in, before onboarding.
 *
 * States plainly what we collect and the guarantee that only aggregated, anonymised characteristics
 * are ever shown. Consent is recorded server-side (with the policy version) before the user can
 * continue into the characteristics wizard.
 */
export function PrivacyConsentScreen() {
    const router = useRouter();
    const { isDark } = useTheme();
    const e = getEditorial(isDark);
    const setConsentedAt = useAuthStore((s) => s.setConsentedAt);
    const [submitting, setSubmitting] = useState(false);

    const onAgree = async () => {
        setSubmitting(true);
        try {
            const consentedAt = await recordConsent();
            setConsentedAt(consentedAt ?? new Date().toISOString());
            router.replace("/usercharacteristics");
        } catch {
            Alert.alert("Couldn’t save", "We couldn’t record your consent. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
            <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
                <Text style={[styles.eyebrow, { color: e.muted }]}>BEFORE YOU START</Text>
                <Text style={[styles.title, { color: e.ink }]}>
                    Anonymous <Text style={{ color: e.teal }}>by design.</Text>
                </Text>
                <Text style={[styles.lede, { color: e.secondary }]}>
                    Your Say News shows how different kinds of people feel about a story. To do that we
                    ask about you — but we keep who you are and how you vote strictly apart.
                </Text>

                <View style={styles.list}>
                    {PROMISES.map((p, i) => (
                        <View
                            key={i}
                            style={[
                                styles.row,
                                {
                                    backgroundColor: p.kind === "do" ? e.privacyBg : e.surface,
                                    borderColor: p.kind === "do" ? e.privacyBorder : e.border,
                                },
                            ]}
                        >
                            <View
                                style={[
                                    styles.dot,
                                    { backgroundColor: p.kind === "do" ? e.teal : e.coral },
                                ]}
                            />
                            <Text
                                style={[
                                    styles.rowText,
                                    { color: p.kind === "do" ? e.privacyText : e.secondary },
                                ]}
                            >
                                {p.text}
                            </Text>
                        </View>
                    ))}
                </View>
            </ScrollView>

            <View style={styles.footer}>
                <Pressable
                    onPress={onAgree}
                    disabled={submitting}
                    style={[styles.cta, { backgroundColor: e.lime, opacity: submitting ? 0.7 : 1 }]}
                >
                    <Text style={[styles.ctaLabel, { color: e.onLime }]}>
                        {submitting ? "Saving…" : "I agree — continue"}
                    </Text>
                </Pressable>
                <View style={styles.privacyLine}>
                    <View style={[styles.limeDot, { backgroundColor: e.lime }]} />
                    <Text style={[styles.privacyLineText, { color: e.muted }]}>
                        You can review this any time in Settings.
                    </Text>
                </View>
            </View>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    screen: { flex: 1 },
    content: { paddingHorizontal: 26, paddingTop: 18, paddingBottom: 24 },
    eyebrow: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 1.6, marginBottom: 14 },
    title: {
        fontFamily: EditorialFont.serif,
        fontSize: 38,
        lineHeight: 40,
        letterSpacing: -0.6,
    },
    lede: {
        fontFamily: EditorialFont.sans,
        fontSize: 15,
        lineHeight: 22,
        marginTop: 16,
    },
    list: { marginTop: 26, gap: 11 },
    row: {
        flexDirection: "row",
        gap: 12,
        borderWidth: 1,
        borderRadius: 14,
        padding: 14,
    },
    dot: { width: 9, height: 9, borderRadius: 5, marginTop: 5 },
    rowText: { flex: 1, fontFamily: EditorialFont.sansMedium, fontSize: 13.5, lineHeight: 20 },
    footer: { paddingHorizontal: 26, paddingTop: 6, gap: 14 },
    cta: {
        height: 54,
        borderRadius: 14,
        alignItems: "center",
        justifyContent: "center",
    },
    ctaLabel: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 16 },
    privacyLine: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 7,
        paddingBottom: 6,
    },
    limeDot: { width: 5, height: 5, borderRadius: 3 },
    privacyLineText: { fontFamily: EditorialFont.mono, fontSize: 10.5 },
});
