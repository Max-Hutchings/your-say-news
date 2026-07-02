import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
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

/**
 * The create-post experience in the editorial design language (design handoff).
 * A compose header, a Manual / Pepper AI mode switch, then either the manual
 * form — headline (serif), summary, the support question inverted onto ink, and
 * an optional media well — or the Pepper AI template. Orchestration for the
 * manual path lives in useCreatePost; Pepper is a template only (no wiring yet).
 */

const HEADLINE_MAX = 120;
const SUMMARY_MAX = 2000;

export function CreatePostScreen() {
  const router = useRouter();
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { picked, progress, submitting, error, fieldErrors, pickMedia, removeMedia, submit } =
    useCreatePost();

  const [mode, setMode] = useState<ComposeMode>("manual");
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [supportQuestion, setSupportQuestion] = useState("");

  const handlePublish = async () => {
    const created = await submit({ title, summary, supportQuestion });
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
              {/* HEADLINE */}
              <View style={styles.labelRow}>
                <Eyebrow text="HEADLINE" />
                <Text style={[styles.count, { color: e.chipText }]}>
                  {title.length} / {HEADLINE_MAX}
                </Text>
              </View>
              <View style={[styles.field, { backgroundColor: e.surface, borderColor: e.border }]}>
                <TextInput
                  value={title}
                  onChangeText={setTitle}
                  maxLength={HEADLINE_MAX}
                  placeholder="Schools are banning phones. Grades are climbing."
                  placeholderTextColor={e.muted}
                  multiline
                  style={[styles.headlineInput, { color: e.ink }]}
                />
              </View>
              {fieldErrors.title && (
                <Text style={[styles.fieldError, { color: e.coral }]}>{fieldErrors.title}</Text>
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

              {/* SUPPORT QUESTION — inverted onto ink */}
              <Eyebrow text="SUPPORT QUESTION" style={styles.blockLabel} />
              <View style={[styles.supportBlock, { backgroundColor: e.inkBlock }]}>
                <View style={styles.quoteRow}>
                  <Text style={[styles.quoteMark, { color: e.lime }]}>{"“"}</Text>
                  <TextInput
                    value={supportQuestion}
                    onChangeText={setSupportQuestion}
                    placeholder="Phones should be banned in all schools during class hours."
                    placeholderTextColor={e.onInkBlockMuted}
                    multiline
                    style={[styles.supportInput, { color: e.onInkBlock }]}
                  />
                </View>
                <View style={styles.voteRow}>
                  <View style={[styles.votePill, { borderColor: e.agreePreviewBorder }]}>
                    <Text style={[styles.votePillText, { color: e.agreePreview }]}>AGREE</Text>
                  </View>
                  <View style={[styles.votePill, { borderColor: e.disagreePreviewBorder }]}>
                    <Text style={[styles.votePillText, { color: e.disagreePreview }]}>DISAGREE</Text>
                  </View>
                </View>
                <Text style={[styles.supportHelp, { color: e.onInkBlockMuted }]}>
                  Readers vote once. Phrase it as a clear yes/no motion.
                </Text>
              </View>
              {fieldErrors.supportQuestion && (
                <Text style={[styles.fieldError, { color: e.coral }]}>
                  {fieldErrors.supportQuestion}
                </Text>
              )}

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
  blockLabel: {
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
  headlineInput: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 20,
    lineHeight: 23,
    padding: 0,
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
    fontSize: 16.5,
    lineHeight: 22,
    padding: 0,
    minHeight: 44,
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
});
