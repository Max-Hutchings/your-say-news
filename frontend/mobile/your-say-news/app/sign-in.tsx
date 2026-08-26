/**
 * Sign In / Welcome — the brand-forward editorial entry screen.
 *
 * Shows the value before asking for anything, then signs local developers into the Firebase
 * Authentication Emulator with one of the repository's seeded test accounts.
 */
import { useState } from "react";
import { View, Text, TextInput, Pressable, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAuthStore } from "@/features/auth";
import { getEditorial, EditorialFont } from "@/constants/theme";

// The login screen is a fixed dark brand moment regardless of system theme.
const e = getEditorial(true);

export default function SignInScreen() {
    const { login } = useAuthStore();
    const [email, setEmail] = useState("riley.reader@example.com");
    const [password, setPassword] = useState("password123");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const onContinue = async () => {
        try {
            setBusy(true);
            setError(null);
            if (!await login(email.trim(), password)) {
                setError("That test account could not be signed in.");
            }
        } catch {
            setError("The local authentication service is not available.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
            <View style={styles.body}>
                {/* Brand lockup */}
                <View style={styles.brandRow}>
                    <View style={[styles.logo, { backgroundColor: e.lime }]}>
                        <Text style={[styles.logoY, { color: e.onLime }]}>Y</Text>
                    </View>
                    <Text style={[styles.wordmark, { color: e.ink }]}>Your Say News</Text>
                </View>

                {/* Editorial headline */}
                <View style={styles.headlineBlock}>
                    <Text style={[styles.eyebrow, { color: e.muted }]}>EVERYONE’S READING IT</Text>
                    <Text style={[styles.headline, { color: e.ink }]}>
                        Nobody <Text style={[styles.headlineItalic, { color: e.lime }]}>agrees</Text> on it.
                        See who.
                    </Text>
                    <Text style={[styles.subcopy, { color: e.secondary }]}>
                        Vote on the day’s tech stories and unlock how people like you — and people
                        unlike you — really feel.
                    </Text>
                </View>

                {/* Locked teaser card */}
                <View style={[styles.teaser, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
                    <Text style={[styles.teaserEyebrow, { color: e.muted }]}>TODAY · AI POLICY</Text>
                    <Text style={[styles.teaserMotion, { color: e.ink }]}>
                        “Big Tech should be liable for AI-generated misinformation.”
                    </Text>
                    <View style={[styles.splitTrack, { backgroundColor: e.track }]}>
                        <View style={[styles.splitAgree, { backgroundColor: e.teal }]} />
                        <View style={[styles.splitDisagree, { backgroundColor: e.coral }]} />
                    </View>
                    <View style={styles.splitLabels}>
                        <Text style={[styles.splitLabel, { color: e.teal }]}>58% AGREE</Text>
                        <Text style={[styles.splitLabel, { color: e.coral }]}>42% DISAGREE</Text>
                    </View>
                    <View style={[styles.lockRow, { borderTopColor: e.track }]}>
                        <Lock color={e.muted} />
                        <Text style={[styles.lockText, { color: e.muted }]}>
                            Sign in to see how people like you split
                        </Text>
                    </View>
                </View>
            </View>

            {/* Local sign-in sheet */}
            <View style={[styles.sheet, { backgroundColor: e.surfaceAlt, borderTopColor: e.border }]}>
                <Text style={[styles.sheetEyebrow, { color: e.lime }]}>LOCAL TEST ACCOUNT</Text>
                <TextInput
                    accessibilityLabel="Email"
                    autoCapitalize="none"
                    autoComplete="email"
                    keyboardType="email-address"
                    value={email}
                    onChangeText={setEmail}
                    style={[styles.input, { color: e.ink, borderColor: e.border }]}
                />
                <TextInput
                    accessibilityLabel="Password"
                    autoCapitalize="none"
                    autoComplete="password"
                    secureTextEntry
                    value={password}
                    onChangeText={setPassword}
                    style={[styles.input, { color: e.ink, borderColor: e.border }]}
                />
                <Pressable
                    accessibilityRole="button"
                    onPress={onContinue}
                    disabled={busy}
                    style={[styles.primary, { backgroundColor: e.lime, opacity: busy ? 0.7 : 1 }]}
                >
                    <Lock color={e.onLime} />
                    <Text style={[styles.primaryLabel, { color: e.onLime }]}>
                        {busy ? "Signing in..." : "Sign in"}
                    </Text>
                </Pressable>
                {error ? <Text style={[styles.error, { color: e.coral }]}>{error}</Text> : null}
                <View style={styles.privacyLine}>
                    <View style={[styles.limeDot, { backgroundColor: e.lime }]} />
                    <Text style={[styles.privacyLineText, { color: e.muted }]}>
                        Anonymous by design — your name is never shown beside a vote
                    </Text>
                </View>
            </View>
        </SafeAreaView>
    );
}

/** A small drawn padlock, matching the editorial line-art style. */
function Lock({ color }: { color: string }) {
    return (
        <View style={styles.lock}>
            <View style={[styles.lockShackle, { borderColor: color }]} />
            <View style={[styles.lockBody, { backgroundColor: color }]} />
        </View>
    );
}

const styles = StyleSheet.create({
    screen: { flex: 1, justifyContent: "space-between" },
    body: { paddingHorizontal: 26, paddingTop: 16 },
    brandRow: { flexDirection: "row", alignItems: "center", gap: 10 },
    logo: { width: 30, height: 30, borderRadius: 8, alignItems: "center", justifyContent: "center" },
    logoY: { fontFamily: EditorialFont.serif, fontSize: 20, fontWeight: "600" },
    wordmark: { fontFamily: EditorialFont.serif, fontSize: 20, letterSpacing: -0.2 },
    headlineBlock: { marginTop: 36 },
    eyebrow: { fontFamily: EditorialFont.mono, fontSize: 11, letterSpacing: 1.6, marginBottom: 14 },
    headline: { fontFamily: EditorialFont.serif, fontSize: 42, lineHeight: 44, letterSpacing: -0.8 },
    headlineItalic: { fontFamily: EditorialFont.serifItalic, fontStyle: "italic" },
    subcopy: { fontFamily: EditorialFont.sans, fontSize: 15, lineHeight: 22, marginTop: 16 },
    teaser: { marginTop: 28, borderWidth: 1, borderRadius: 16, padding: 16 },
    teaserEyebrow: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 1.2, marginBottom: 8 },
    teaserMotion: { fontFamily: EditorialFont.serif, fontSize: 18, lineHeight: 22 },
    splitTrack: { flexDirection: "row", height: 11, borderRadius: 6, overflow: "hidden", marginTop: 14 },
    splitAgree: { flex: 58 },
    splitDisagree: { flex: 42 },
    splitLabels: { flexDirection: "row", justifyContent: "space-between", marginTop: 8 },
    splitLabel: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10.5, fontWeight: "600" },
    lockRow: {
        flexDirection: "row",
        alignItems: "center",
        gap: 7,
        marginTop: 12,
        paddingTop: 12,
        borderTopWidth: 1,
    },
    lockText: { fontFamily: EditorialFont.sans, fontSize: 12 },
    sheet: {
        borderTopLeftRadius: 28,
        borderTopRightRadius: 28,
        borderTopWidth: 1,
        paddingHorizontal: 26,
        paddingTop: 24,
        paddingBottom: 30,
    },
    sheetEyebrow: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 1.6, marginBottom: 14 },
    input: {
        height: 48,
        borderWidth: 1,
        borderRadius: 12,
        paddingHorizontal: 14,
        marginBottom: 10,
        fontFamily: EditorialFont.sans,
        fontSize: 15,
    },
    primary: {
        height: 54,
        borderRadius: 14,
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 9,
    },
    primaryLabel: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 16 },
    error: { fontFamily: EditorialFont.sans, fontSize: 13, marginTop: 10, textAlign: "center" },
    privacyLine: {
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        gap: 7,
        marginTop: 18,
    },
    limeDot: { width: 5, height: 5, borderRadius: 3 },
    privacyLineText: { fontFamily: EditorialFont.mono, fontSize: 10.5 },
    lock: { width: 15, height: 16, alignItems: "center", justifyContent: "flex-end" },
    lockShackle: {
        width: 9,
        height: 8,
        borderWidth: 1.7,
        borderBottomWidth: 0,
        borderTopLeftRadius: 5,
        borderTopRightRadius: 5,
    },
    lockBody: { width: 13, height: 9, borderRadius: 2.5 },
});
