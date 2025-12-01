// app/(protected)/_layout.tsx
import { Tabs } from "expo-router";
import { Ionicons } from "@expo/vector-icons"; // or any icon lib you like

export default function ProtectedTabsLayout() {
    return (
        <Tabs
            screenOptions={{
                // Style for the bottom tab bar
                headerShown: false,         // Hide header if you want full-screen tabs
                tabBarActiveTintColor: "#007AFF", // Active tab colour
                tabBarInactiveTintColor: "#999999",
            }}
        >

            {/* Home tab */}
            <Tabs.Screen
                name="home"
                options={{
                    title: "Home",
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="home-outline" size={size} color={color} />
                    ),
                }}
            />

            {/* Profile tab */}
            <Tabs.Screen
                name="profile"
                options={{
                    title: "Profile",
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="person-outline" size={size} color={color} />
                    ),
                }}
            />

            {/* Settings tab */}
            <Tabs.Screen
                name="settings"
                options={{
                    title: "Settings",
                    tabBarIcon: ({ color, size }) => (
                        <Ionicons name="settings-outline" size={size} color={color} />
                    ),
                }}
            />

        </Tabs>
    );
}
