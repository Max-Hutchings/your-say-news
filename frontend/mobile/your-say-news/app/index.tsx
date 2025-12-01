
import { useEffect } from "react";
import { View, Text, ActivityIndicator, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import {useAuthStore} from "@/components/auth/authContext";


export default function SplashScreen() {
    const router = useRouter();
    const { isLoggedIn } = useAuthStore();

    useEffect(() => {
        if (!isLoggedIn) {
            router.replace("//(protected)");
        }
    }, [isLoggedIn, router]);

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
