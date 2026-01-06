
import { View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { Redirect } from "expo-router";
import {useAuthStore} from "@/components/auth/authContext";


export default function SplashScreen() {
    const { isLoggedIn, _stateHydrated } = useAuthStore();

    // Wait for auth state to be hydrated before redirecting
    if (!_stateHydrated) {
        return (
            <View style={styles.container}>
                <Text style={styles.title}>YourSay News</Text>
                <Text style={styles.subtitle}>Neutral, people-powered news</Text>
                <ActivityIndicator style={{ marginTop: 20 }} />
            </View>
        );
    }

    // Use Redirect component instead of programmatic navigation
    if (isLoggedIn) {
        return <Redirect href="/(protected)" />;
    }

    return <Redirect href="/sign-in" />;
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: "#000",
        alignItems: "center",
        justifyContent: "center",
        paddingHorizontal: 24,
    },
    title: {
        fontSize: 28,
        fontWeight: "700",
        color: "#fff",
    },
    subtitle: {
        marginTop: 8,
        fontSize: 14,
        color: "#ccc",
        textAlign: "center",
    },
});
