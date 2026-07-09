import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter, type Href } from "expo-router";
import { getEditorial, EditorialFont, useTheme } from "@/constants/theme";
import { useAuthStore } from "@/features/auth";

/**
 * The account hub — opened from the masthead avatar. A small menu to jump to the
 * reader's profile or settings, and to sign out. Signing out clears the session
 * (see {@link useAuthStore.logout}); the root layout's auth guard then swaps the
 * protected stack for the sign-in screen, so there's no manual redirect here.
 */
export function AccountScreen() {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const email = useAuthStore((s) => s.email);
  const firstName = useAuthStore((s) => s.firstName);
  const lastName = useAuthStore((s) => s.lastName);
  const logout = useAuthStore((s) => s.logout);

  const name = [firstName, lastName].filter(Boolean).join(" ") || "Your account";
  const avatarLabel = (firstName ?? email ?? "?").charAt(0).toUpperCase();

  const onLogout = async () => {
    await logout();
  };

  return (
    <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
      <View style={styles.topRow}>
        <Text style={[styles.title, { color: e.ink }]}>Account</Text>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Close"
          style={styles.iconButton}
        >
          <Ionicons name="close" size={24} color={e.ink} />
        </Pressable>
      </View>

      <View style={styles.identityRow}>
        <View style={[styles.avatar, { backgroundColor: e.lime }]}>
          <Text style={[styles.avatarLabel, { color: e.onLime }]}>{avatarLabel}</Text>
        </View>
        <View style={styles.identityText}>
          <Text style={[styles.name, { color: e.ink }]} numberOfLines={1}>
            {name}
          </Text>
          {email ? (
            <Text style={[styles.email, { color: e.muted }]} numberOfLines={1}>
              {email}
            </Text>
          ) : null}
        </View>
      </View>

      <View style={[styles.menu, { borderColor: e.border, backgroundColor: e.surface }]}>
        <MenuRow
          icon="person-outline"
          label="Profile"
          palette={e}
          onPress={() => router.push("/profiles/me" as Href)}
        />
        <View style={[styles.divider, { backgroundColor: e.border }]} />
        <MenuRow
          icon="settings-outline"
          label="Settings"
          palette={e}
          onPress={() => router.push("/settings" as Href)}
        />
      </View>

      <Pressable
        onPress={onLogout}
        accessibilityRole="button"
        accessibilityLabel="Log out"
        style={[styles.logout, { borderColor: e.border }]}
      >
        <Ionicons name="log-out-outline" size={20} color={e.coral} />
        <Text style={[styles.logoutLabel, { color: e.coral }]}>Log out</Text>
      </Pressable>
    </SafeAreaView>
  );
}

function MenuRow({
  icon,
  label,
  palette,
  onPress,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  palette: ReturnType<typeof getEditorial>;
  onPress: () => void;
}) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" style={styles.menuRow}>
      <Ionicons name={icon} size={22} color={palette.ink} />
      <Text style={[styles.menuLabel, { color: palette.ink }]}>{label}</Text>
      <Ionicons name="chevron-forward" size={20} color={palette.muted} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    paddingHorizontal: 22,
  },
  topRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingTop: 8,
  },
  title: {
    fontFamily: EditorialFont.serif,
    fontSize: 28,
  },
  iconButton: {
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  identityRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    marginTop: 20,
    marginBottom: 26,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    alignItems: "center",
    justifyContent: "center",
  },
  avatarLabel: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 22,
  },
  identityText: {
    flex: 1,
  },
  name: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 22,
  },
  email: {
    fontFamily: EditorialFont.mono,
    fontSize: 12,
    marginTop: 3,
  },
  menu: {
    borderWidth: 1,
    borderRadius: 16,
    overflow: "hidden",
  },
  menuRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  menuLabel: {
    flex: 1,
    fontFamily: EditorialFont.sansMedium,
    fontWeight: "500",
    fontSize: 16,
  },
  divider: {
    height: 1,
    marginLeft: 52,
  },
  logout: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    borderWidth: 1,
    borderRadius: 14,
    paddingVertical: 15,
    marginTop: 24,
  },
  logoutLabel: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 15,
  },
});
