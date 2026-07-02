// app/(protected)/_layout.tsx
import { Stack } from "expo-router";
import { useTheme, getEditorial } from "@/constants/theme";

/**
 * Protected area layout.
 *
 * A Stack for now: Home is the only real destination, with onboarding and the
 * about screen pushed on top. A bottom-tab navigator returns once there are
 * multiple top-level destinations (profile, results, settings).
 */
export default function ProtectedLayout() {
    const { isDark } = useTheme();
    const e = getEditorial(isDark);

    return (
        <Stack
            screenOptions={{
                headerShown: false,
                contentStyle: { backgroundColor: e.bg },
            }}
        >
            <Stack.Screen name="index" />
            <Stack.Screen name="consent" />
            <Stack.Screen
                name="create-post"
                options={{ headerShown: false, presentation: "modal" }}
            />
            <Stack.Screen name="(usercharacteristics)/usercharacteristics" />
            <Stack.Screen
                name="about/your-say-news"
                options={{ headerShown: true, title: "About" }}
            />
        </Stack>
    );
}
