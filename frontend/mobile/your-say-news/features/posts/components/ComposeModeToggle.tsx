import React from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";

/** Which way the author is composing: writing it themselves or via Pepper AI. */
export type ComposeMode = "manual" | "pepper";

/**
 * The segmented compose-mode switch (design handoff): "Manual · YOU WRITE IT"
 * and "Pepper AI · DRAFTS FOR YOU". The active cell fills — ink for Manual,
 * lime for Pepper (Pepper is the brand-signal, AI-assisted path).
 */
export function ComposeModeToggle({
  mode,
  onChange,
}: {
  mode: ComposeMode;
  onChange: (mode: ComposeMode) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  const manualActive = mode === "manual";
  const pepperActive = mode === "pepper";

  return (
    <View style={[styles.track, { backgroundColor: e.track }]}>
      <Pressable
        onPress={() => onChange("manual")}
        accessibilityRole="button"
        accessibilityState={{ selected: manualActive }}
        style={[styles.cell, manualActive && { backgroundColor: e.ink }]}
      >
        <Text style={[styles.cellTitle, { color: manualActive ? e.bg : e.secondary }]}>Manual</Text>
        <Text style={[styles.cellSub, { color: manualActive ? e.muted : e.muted }]}>
          YOU WRITE IT
        </Text>
      </Pressable>

      <Pressable
        onPress={() => onChange("pepper")}
        accessibilityRole="button"
        accessibilityState={{ selected: pepperActive }}
        style={[styles.cell, pepperActive && { backgroundColor: e.lime }]}
      >
        <Text style={[styles.cellTitle, { color: pepperActive ? e.onLime : e.secondary }]}>
          <Text style={styles.sparkle}>✦ </Text>Pepper AI
        </Text>
        <Text style={[styles.cellSub, { color: pepperActive ? e.onLime : e.muted }]}>
          DRAFTS FOR YOU
        </Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    flexDirection: "row",
    gap: 6,
    borderRadius: 13,
    padding: 4,
  },
  cell: {
    flex: 1,
    height: 44,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  cellTitle: {
    fontFamily: EditorialFont.sansBold,
    fontWeight: "700",
    fontSize: 13,
  },
  sparkle: {
    fontSize: 11,
  },
  cellSub: {
    fontFamily: EditorialFont.mono,
    fontSize: 8,
    letterSpacing: 0.8,
    marginTop: 1,
  },
});
