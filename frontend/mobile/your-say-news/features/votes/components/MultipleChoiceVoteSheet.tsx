import React, { useEffect, useState } from "react";
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import type { VoteErrorKind } from "../types";
import type { VoteOption } from "@/features/posts";

export function MultipleChoiceVoteSheet({
  visible, supportQuestion, options, submitting, error, onSubmit, onClose,
}: {
  visible: boolean;
  supportQuestion: string;
  options: VoteOption[];
  submitting: boolean;
  error: VoteErrorKind | null;
  onSubmit: (optionId: number) => void;
  onClose: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [selected, setSelected] = useState<number | null>(null);
  useEffect(() => { if (!visible) setSelected(null); }, [visible]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={[styles.backdrop, { backgroundColor: e.mediaScrim }]}>
        <Pressable
          style={StyleSheet.absoluteFill}
          accessibilityRole="button"
          accessibilityLabel="Close choice sheet"
          onPress={onClose}
        />
        <View style={[styles.sheet, { backgroundColor: e.bg, borderColor: e.border }]}>
          <View style={[styles.handle, { backgroundColor: e.border }]} />
          <View style={styles.header}>
            <Text style={[styles.eyebrow, { color: e.muted }]}>HAVE YOUR SAY</Text>
            <Pressable accessibilityRole="button" accessibilityLabel="Close choices" onPress={onClose}>
              <Ionicons name="close" size={24} color={e.muted} />
            </Pressable>
          </View>
          <Text accessibilityRole="header" style={[styles.question, { color: e.ink }]}>{supportQuestion}</Text>
          <ScrollView contentContainerStyle={styles.options}>
            {options.map((option) => {
              const chosen = selected === option.id;
              return (
                <Pressable
                  key={option.id}
                  accessibilityRole="radio"
                  accessibilityLabel={option.label}
                  accessibilityState={{ selected: chosen, disabled: submitting }}
                  disabled={submitting}
                  onPress={() => setSelected(option.id)}
                  style={[styles.option, { borderColor: chosen ? e.teal : e.border, backgroundColor: e.surface }]}
                >
                  <View style={[styles.radio, { borderColor: chosen ? e.teal : e.muted }]}>
                    {chosen && <View style={[styles.dot, { backgroundColor: e.teal }]} />}
                  </View>
                  <Text style={[styles.optionLabel, { color: e.ink }]}>{option.label}</Text>
                </Pressable>
              );
            })}
          </ScrollView>
          {error && <Text style={[styles.error, { color: e.coral }]}>{messageFor(error)}</Text>}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Submit choice"
            accessibilityState={{ disabled: selected === null || submitting }}
            disabled={selected === null || submitting}
            onPress={() => selected !== null && onSubmit(selected)}
            style={[styles.submit, { backgroundColor: e.voteAgreeFill, opacity: selected === null ? 0.45 : 1 }]}
          >
            {submitting && <ActivityIndicator size="small" color={e.voteAgreeOnFill} />}
            <Text style={[styles.submitText, { color: e.voteAgreeOnFill }]}>Submit choice</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

function messageFor(error: VoteErrorKind) {
  if (error === "auth") return "Please sign in again to vote.";
  if (error === "network") return "No connection. Try submitting again.";
  return "Couldn't record your choice. Try again.";
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: "flex-end" },
  sheet: { height: "86%", borderWidth: 1, borderTopLeftRadius: 22, borderTopRightRadius: 22, padding: 20 },
  handle: { alignSelf: "center", width: 40, height: 4, borderRadius: 2, marginBottom: 14 },
  header: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  eyebrow: { fontFamily: EditorialFont.monoSemiBold, fontSize: 11, letterSpacing: 1 },
  question: { fontFamily: EditorialFont.serif, fontSize: 24, lineHeight: 31, marginVertical: 18 },
  options: { gap: 10, paddingBottom: 16 },
  option: { minHeight: 58, borderWidth: 1.5, borderRadius: 14, padding: 14, flexDirection: "row", alignItems: "center", gap: 12 },
  radio: { width: 22, height: 22, borderWidth: 2, borderRadius: 11, alignItems: "center", justifyContent: "center" },
  dot: { width: 10, height: 10, borderRadius: 5 },
  optionLabel: { flex: 1, fontFamily: EditorialFont.sansSemiBold, fontSize: 16, lineHeight: 22 },
  submit: { height: 54, borderRadius: 15, flexDirection: "row", justifyContent: "center", alignItems: "center", gap: 9 },
  submitText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 16 },
  error: { fontFamily: EditorialFont.sans, fontSize: 12, marginBottom: 8 },
});
