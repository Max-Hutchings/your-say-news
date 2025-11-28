
import { useEffect } from "react";
import { View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { useAuth } from "./contexts/AuthContext";

export default function SplashScreen() {
    const router = useRouter();
    const { loading } = useAuth();

    useEffect(() => {
        if (!loading) {
            router.replace("/home");
        }
    }, [loading, router]);

    return (
        <View style={styles.container}>
            <Text style={styles.title}>YourSay News</Text>
            <Text style={styles.subtitle}>Neutral, people-powered news</Text>
            <ActivityIndicator style={{ marginTop: 20 }} />
        </View>
    );
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
