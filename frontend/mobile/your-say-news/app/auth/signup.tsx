
import { View, Text, TextInput, TouchableOpacity, StyleSheet } from "react-native";
import { useState } from "react";
import { useRouter, Link } from "expo-router";
import { useAuth } from "../contexts/AuthContext";

export default function Signup() {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const { login } = useAuth();
    const router = useRouter();

    const handleSignup = async () => {

        const token = "dummy-token";
        await login(token);
        router.replace("/home");
    };

    return (
        <View style={styles.container}>
            <Text style={styles.title}>Create your account</Text>

            <TextInput
                placeholder="Email"
                autoCapitalize="none"
                keyboardType="email-address"
                value={email}
                onChangeText={setEmail}
                style={styles.input}
            />

            <TextInput
                placeholder="Password"
                secureTextEntry
                value={password}
                onChangeText={setPassword}
                style={styles.input}
            />

            <TouchableOpacity style={styles.button} onPress={handleSignup}>
                <Text style={styles.buttonText}>Sign up</Text>
            </TouchableOpacity>

            <Link href="/auth/login" asChild>
                <TouchableOpacity>
                    <Text style={styles.link}>
                        Already have an account? Log in
                    </Text>
                </TouchableOpacity>
            </Link>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 16, justifyContent: "center" },
    title: { fontSize: 24, fontWeight: "700", marginBottom: 24 },
    input: {
        borderWidth: 1,
        borderColor: "#ccc",
        borderRadius: 8,
        paddingHorizontal: 10,
        paddingVertical: 8,
        marginBottom: 12,
    },
    button: {
        backgroundColor: "#000",
        borderRadius: 8,
        paddingVertical: 12,
        alignItems: "center",
        marginTop: 8,
    },
    buttonText: { color: "#fff", fontWeight: "600" },
    link: {
        marginTop: 16,
        textAlign: "center",
        color: "#007AFF",
    },
});
