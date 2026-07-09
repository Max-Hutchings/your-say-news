import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import { getEditorial, EditorialFont, useTheme } from "@/constants/theme";

/**
 * Settings — for now a single Appearance section that lets the reader pin the app
 * to Light or Dark. Selecting an option calls the theme provider's setColorScheme,
 * overriding the system preference for the session.
 */
export function SettingsScreen() {
  const router = useRouter();
  const { isDark, colorScheme, setColorScheme } = useTheme();
  const e = getEditorial(isDark);

  return (
    <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
      <View style={styles.topRow}>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Back"
          style={styles.iconButton}
        >
          <Ionicons name="chevron-back" size={24} color={e.ink} />
        </Pressable>
        <Text style={[styles.title, { color: e.ink }]}>Settings</Text>
        <View style={styles.iconButton} />
      </View>

      <Text style={[styles.sectionLabel, { color: e.muted }]}>APPEARANCE</Text>
      <View style={[styles.options, { borderColor: e.border, backgroundColor: e.surface }]}>
        <ThemeOption
          label="Light"
          icon="sunny-outline"
          selected={colorScheme === "light"}
          palette={e}
          onPress={() => setColorScheme("light")}
        />
        <View style={[styles.divider, { backgroundColor: e.border }]} />
        <ThemeOption
          label="Dark"
          icon="moon-outline"
          selected={colorScheme === "dark"}
          palette={e}
          onPress={() => setColorScheme("dark")}
        />
      </View>
    </SafeAreaView>
  );
}

function ThemeOption({
  label,
  icon,
  selected,
  palette,
  onPress,
}: {
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
  selected: boolean;
  palette: ReturnType<typeof getEditorial>;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      style={styles.optionRow}
    >
      <Ionicons name={icon} size={22} color={palette.ink} />
      <Text style={[styles.optionLabel, { color: palette.ink }]}>{label}</Text>
      {selected ? (
        <Ionicons name="checkmark-circle" size={22} color={palette.teal} />
      ) : (
        <View style={[styles.emptyMark, { borderColor: palette.border }]} />
      )}
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
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 16,
  },
  iconButton: {
    width: 36,
    height: 36,
    alignItems: "center",
    justifyContent: "center",
  },
  sectionLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
    letterSpacing: 1.5,
    marginTop: 24,
    marginBottom: 10,
    marginLeft: 4,
  },
  options: {
    borderWidth: 1,
    borderRadius: 16,
    overflow: "hidden",
  },
  optionRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    paddingVertical: 16,
    paddingHorizontal: 16,
  },
  optionLabel: {
    flex: 1,
    fontFamily: EditorialFont.sansMedium,
    fontWeight: "500",
    fontSize: 16,
  },
  divider: {
    height: 1,
    marginLeft: 52,
  },
  emptyMark: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
  },
});
