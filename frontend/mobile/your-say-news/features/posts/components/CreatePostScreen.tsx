import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Alert,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useRouter } from "expo-router";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { Eyebrow } from "@/components/ui";
import { useCreatePost } from "../hooks/use-create-post";
import { ComposeHeader } from "./ComposeHeader";
import { ComposeModeToggle, type ComposeMode } from "./ComposeModeToggle";
import { ComposeMediaField } from "./ComposeMediaField";
import { PepperCompose } from "./PepperCompose";
import type { VotingType } from "../types";
import { OptionReorderHandle } from "./OptionReorderHandle";

/**
 * The create-post experience in the editorial design language (design handoff).
 * A compose header, a Manual / Pepper AI mode switch, then either the manual
 * form — the support question as its primary heading, summary, and
 * an optional media well — or the Pepper AI template. Orchestration for the
 * manual path lives in useCreatePost; Pepper is a template only (no wiring yet).
 */

const SUMMARY_MAX = 2000;
const SUPPORT_QUESTION_MAX = 512;

export function CreatePostScreen() {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { picked, progress, submitting, error, fieldErrors, pickMedia, removeMedia, submit } =
    useCreatePost();

  const [mode, setMode] = useState<ComposeMode>("manual");
  const [summary, setSummary] = useState("");
  const [supportQuestion, setSupportQuestion] = useState("");
  const [votingType, setVotingType] = useState<VotingType>("BINARY");
  const [voteOptions, setVoteOptions] = useState(["", ""]);
  const [showArguments, setShowArguments] = useState(false);
  const [caseFor, setCaseFor] = useState("");
  const [caseAgainst, setCaseAgainst] = useState("");

  const handlePublish = async () => {
    const created = await submit({ summary, supportQuestion, votingType, voteOptions, caseFor, caseAgainst });
    if (created) router.back();
  };

  return (
    <SafeAreaView style={[styles.screen, { backgroundColor: e.bg }]} edges={["top", "bottom"]}>
      <ComposeHeader onBack={() => router.back()} onPost={handlePublish} posting={submitting} />

      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          style={styles.flex}
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <ComposeModeToggle mode={mode} onChange={setMode} />

          {mode === "pepper" ? (
            <PepperCompose />
          ) : (
            <>
              {/* SUPPORT QUESTION — the post's title, inverted onto ink */}
              <View style={styles.labelRow}>
                <Eyebrow text="SUPPORT QUESTION" />
                <Text style={[styles.count, { color: e.chipText }]}>
                  {supportQuestion.length} / {SUPPORT_QUESTION_MAX}
                </Text>
              </View>
              <View style={[styles.supportBlock, { backgroundColor: e.inkBlock }]}>
                <View style={styles.quoteRow}>
                  <Text style={[styles.quoteMark, { color: e.lime }]}>{"“"}</Text>
                  <TextInput
                    value={supportQuestion}
                    onChangeText={setSupportQuestion}
                    maxLength={SUPPORT_QUESTION_MAX}
                    placeholder="Should phones be banned in schools during class hours?"
                    placeholderTextColor={e.onInkBlockMuted}
                    multiline
                    style={[styles.supportInput, { color: e.onInkBlock }]}
                  />
                </View>
                {votingType === "BINARY" && <View style={styles.voteRow}>
                  <View style={[styles.votePill, { borderColor: e.agreePreviewBorder }]}>
                    <Text style={[styles.votePillText, { color: e.agreePreview }]}>AGREE</Text>
                  </View>
                  <View style={[styles.votePill, { borderColor: e.disagreePreviewBorder }]}>
                    <Text style={[styles.votePillText, { color: e.disagreePreview }]}>DISAGREE</Text>
                  </View>
                </View>}
                <Text style={[styles.supportHelp, { color: e.onInkBlockMuted }]}>
                  {votingType === "BINARY"
                    ? "This becomes the post title. Make it clear and answerable with Agree or Disagree."
                    : "This becomes the post title. Voters can select one option."}
                </Text>
              </View>
              {fieldErrors.supportQuestion && (
                <Text style={[styles.fieldError, { color: e.coral }]}>
                  {fieldErrors.supportQuestion}
                </Text>
              )}

              <View style={styles.votingTypeRow}>
                <View style={styles.votingTypeCopy}>
                  <Text style={[styles.fieldLabel, { color: e.ink }]}>Multiple choice</Text>
                  <Text style={[styles.supportHelp, { color: e.muted }]}>Voters can select one option.</Text>
                </View>
                <Pressable accessibilityRole="switch" accessibilityLabel="Multiple choice"
                  accessibilityState={{ checked: votingType === "MULTIPLE_CHOICE" }}
                  onPress={() => setVotingType((current) => current === "BINARY" ? "MULTIPLE_CHOICE" : "BINARY")}
                  style={[styles.switchTrack, { backgroundColor: votingType === "MULTIPLE_CHOICE" ? e.teal : e.border }]}>
                  <View style={[styles.switchThumb, votingType === "MULTIPLE_CHOICE" && styles.switchThumbOn]} />
                </Pressable>
              </View>

              {votingType === "MULTIPLE_CHOICE" && (
                <View style={styles.optionList}>
                  {voteOptions.map((label, index) => (
                    <View key={index} style={styles.optionRow}>
                      <TextInput testID={`vote-option-${index}`} accessibilityLabel={`Choice ${index + 1}`}
                        value={label} maxLength={120} onChangeText={(value) => setVoteOptions((current) =>
                          current.map((item, itemIndex) => itemIndex === index ? value : item))}
                        placeholder={`Choice ${index + 1}`} placeholderTextColor={e.muted}
                        style={[styles.optionInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]} />
                      <View style={styles.orderButtons}>
                        <OptionReorderHandle color={e.muted} canMoveUp={index > 0}
                          canMoveDown={index < voteOptions.length - 1}
                          onMoveUp={() => setVoteOptions((current) => move(current, index, index - 1))}
                          onMoveDown={() => setVoteOptions((current) => move(current, index, index + 1))} />
                        <Pressable accessibilityRole="button" accessibilityLabel={`Move choice ${index + 1} up`}
                          disabled={index === 0} onPress={() => setVoteOptions((current) => move(current, index, index - 1))}>
                          <Text style={{ color: index === 0 ? e.muted : e.ink }}>↑</Text>
                        </Pressable>
                        <Pressable accessibilityRole="button" accessibilityLabel={`Move choice ${index + 1} down`}
                          disabled={index === voteOptions.length - 1}
                          onPress={() => setVoteOptions((current) => move(current, index, index + 1))}>
                          <Text style={{ color: index === voteOptions.length - 1 ? e.muted : e.ink }}>↓</Text>
                        </Pressable>
                        {voteOptions.length > 2 && <Pressable accessibilityRole="button"
                          accessibilityLabel={`Remove choice ${index + 1}`}
                          onPress={() => setVoteOptions((current) => current.filter((_, i) => i !== index))}>
                          <Text style={{ color: e.coral }}>×</Text>
                        </Pressable>}
                      </View>
                    </View>
                  ))}
                  {voteOptions.length < 5 && <Pressable accessibilityRole="button" accessibilityLabel="Add option"
                    onPress={() => setVoteOptions((current) => [...current, ""])}>
                    <Text style={[styles.addText, { color: e.teal }]}>+ Add option</Text>
                  </Pressable>}
                  {fieldErrors.voteOptions && <Text style={[styles.fieldError, { color: e.coral }]}>{fieldErrors.voteOptions}</Text>}
                </View>
              )}

              {/* SUMMARY */}
              <View style={styles.labelRow}>
                <Eyebrow text="SUMMARY" />
                <Text style={[styles.count, { color: e.chipText }]}>
                  {summary.length} / {SUMMARY_MAX}
                </Text>
              </View>
              <View style={[styles.field, { backgroundColor: e.surface, borderColor: e.border }]}>
                <TextInput
                  value={summary}
                  onChangeText={setSummary}
                  maxLength={SUMMARY_MAX}
                  placeholder="Give readers the context they need before voting."
                  placeholderTextColor={e.muted}
                  multiline
                  style={[styles.summaryInput, { color: e.chipText }]}
                />
              </View>
              {fieldErrors.summary && (
                <Text style={[styles.fieldError, { color: e.coral }]}>{fieldErrors.summary}</Text>
              )}

              <Pressable accessibilityRole="checkbox" accessibilityLabel="Add supporting arguments"
                accessibilityState={{ checked: showArguments }} style={styles.argumentToggle}
                onPress={() => {
                  if (showArguments && (caseFor.trim() || caseAgainst.trim())) {
                    Alert.alert("Remove supporting arguments?", "The text you entered will be cleared.", [
                      { text: "Keep", style: "cancel" },
                      { text: "Remove", style: "destructive", onPress: () => { setShowArguments(false); setCaseFor(""); setCaseAgainst(""); } },
                    ]);
                  } else setShowArguments((current) => !current);
                }}>
                <Text style={[styles.addText, { color: e.teal }]}>{showArguments ? "✓ " : "+ "}Add supporting arguments</Text>
              </Pressable>
              {showArguments && <View style={styles.argumentFields}>
                <TextInput accessibilityLabel="Case for" value={caseFor} onChangeText={setCaseFor}
                  maxLength={512} placeholder="Optional case for" placeholderTextColor={e.muted}
                  style={[styles.optionInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]} />
                <TextInput accessibilityLabel="Case against" value={caseAgainst} onChangeText={setCaseAgainst}
                  maxLength={512} placeholder="Optional case against" placeholderTextColor={e.muted}
                  style={[styles.optionInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]} />
              </View>}

              {/* MEDIA */}
              <View style={styles.mediaSpacer}>
                <ComposeMediaField
                  media={picked}
                  progress={progress}
                  uploading={submitting && picked.length > 0}
                  onPick={pickMedia}
                  onRemove={removeMedia}
                />
              </View>

              {error && <Text style={[styles.fieldError, { color: e.coral }]}>{error}</Text>}
            </>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function move<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length) return items;
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  content: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 40,
  },
  labelRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 16,
    marginBottom: 7,
  },
  count: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.5,
  },
  field: {
    borderWidth: 1.5,
    borderRadius: 13,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  summaryInput: {
    fontFamily: EditorialFont.sans,
    fontSize: 13.5,
    lineHeight: 21,
    padding: 0,
    minHeight: 80,
    textAlignVertical: "top",
  },
  supportBlock: {
    borderRadius: 14,
    padding: 15,
  },
  quoteRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  quoteMark: {
    fontFamily: EditorialFont.serif,
    fontSize: 30,
    lineHeight: 24,
  },
  supportInput: {
    flex: 1,
    fontFamily: EditorialFont.serifItalic,
    fontStyle: "italic",
    fontSize: 20,
    lineHeight: 26,
    padding: 0,
    minHeight: 58,
    textAlignVertical: "top",
  },
  voteRow: {
    flexDirection: "row",
    gap: 8,
    marginTop: 13,
  },
  votePill: {
    flex: 1,
    height: 30,
    borderRadius: 9,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  votePillText: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.8,
  },
  supportHelp: {
    fontFamily: EditorialFont.sans,
    fontSize: 11,
    marginTop: 10,
  },
  mediaSpacer: {
    marginTop: 16,
  },
  fieldError: {
    fontFamily: EditorialFont.sans,
    fontSize: 12,
    marginTop: 6,
  },
  votingTypeRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 16 },
  votingTypeCopy: { flex: 1 },
  fieldLabel: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 15 },
  switchTrack: { width: 48, height: 28, borderRadius: 14, padding: 3 },
  switchThumb: { width: 22, height: 22, borderRadius: 11, backgroundColor: "#FFFFFF" },
  switchThumbOn: { alignSelf: "flex-end" },
  optionList: { marginTop: 12, gap: 9 },
  optionRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  optionInput: { flex: 1, minHeight: 48, borderWidth: 1.5, borderRadius: 12, paddingHorizontal: 12, fontFamily: EditorialFont.sans, fontSize: 14 },
  orderButtons: { flexDirection: "row", alignItems: "center", gap: 10 },
  addText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 14 },
  argumentToggle: { marginTop: 16, paddingVertical: 8 },
  argumentFields: { gap: 10 },
});
