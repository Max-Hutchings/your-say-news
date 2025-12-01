
import { View, Text, Button, StyleSheet } from "react-native";
import { useRouter } from "expo-router";
import { useAuth } from "../contexts/AuthContext";

export default function Home() {
    const router = useRouter();
    const { user } = useAuth();

    const handleVote = (vote: "agree" | "disagree") => {
        if (!user) {
            router.push("/auth/signup");
            return;
        }

        if (!user.hasCharacteristics) {
            router.push("/usercharacteristics");
            return;
        }

        console.log("Submitting vote:", vote);
    };

    return (
        <View style={styles.container}>
            <Text style={styles.heading}>Neutral News Feed</Text>

            <View style={styles.card}>
                <Text style={styles.cardText}>[News item / video placeholder]</Text>

                <Button title="I agree" onPress={() => handleVote("agree")} />
                <View style={{ height: 8 }} />
                <Button title="I disagree" onPress={() => handleVote("disagree")} />
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 16 },
    heading: { fontSize: 20, fontWeight: "600" },
    card: {
        marginTop: 24,
        padding: 16,
        borderRadius: 12,
        borderWidth: 1,
        borderColor: "#ddd",
    },
    cardText: { marginBottom: 12, fontSize: 16 },
});
