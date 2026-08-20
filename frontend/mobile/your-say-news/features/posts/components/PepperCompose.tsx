import React, { useEffect, useState } from "react";
import { View, Text, TextInput, Pressable, StyleSheet } from "react-native";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { usePepperDraft } from "../hooks/use-pepper-draft";
import type { PepperDraftRecord, PepperPostDraft } from "../types";

export function PepperCompose({
  onDraftChange,
}: {
  onDraftChange: (draft: PepperDraftRecord | null) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const { draft, status, loading, error, generate, changeDraft } = usePepperDraft();
  const [prompt, setPrompt] = useState("");
  const [promptFocused, setPromptFocused] = useState(false);

  useEffect(() => {
    if (draft?.prompt) setPrompt(draft.prompt);
    onDraftChange(draft);
  }, [draft, onDraftChange]);

  const update = (content: PepperPostDraft) => {
    changeDraft(content);
    if (draft) onDraftChange({ ...draft, content });
  };
  const content = draft?.content;

  return (
    <View style={styles.container}>
      <Text style={[styles.label, { color: e.muted }]}>PROMPT</Text>
      <View style={[styles.promptBox, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
        <View style={styles.promptIntro}>
          <View style={[styles.avatar, { backgroundColor: e.lime }]}>
            <Text style={[styles.avatarGlyph, { color: e.onLime }]}>✦</Text>
          </View>
          <Text style={[styles.introText, { color: e.ink }]}>
            Give Pepper a topic to research across reliable sources and turn into an editable post.
          </Text>
        </View>

        <TextInput
          value={prompt}
          onChangeText={setPrompt}
          onFocus={() => setPromptFocused(true)}
          onBlur={() => setPromptFocused(false)}
          placeholder="e.g. The impact of four-day work weeks on productivity and hiring…"
          placeholderTextColor={e.muted}
          multiline
          maxLength={2000}
          style={[
            styles.promptInput,
            promptFocused && styles.promptInputFocused,
            { backgroundColor: e.bg, borderColor: e.border, color: e.secondary },
          ]}
        />

        <View style={styles.promptFooter}>
          <Text style={[styles.footerNote, { color: e.chipText }]}>LIVE SOURCED RESEARCH</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Research and write"
            accessibilityState={{ disabled: loading || !prompt.trim() }}
            disabled={loading || !prompt.trim()}
            onPress={() => void generate(prompt)}
            style={[
              styles.cta,
              { backgroundColor: e.lime, opacity: loading || !prompt.trim() ? 0.45 : 1 },
            ]}
          >
            <Text style={[styles.ctaText, { color: e.onLime }]}>
              {loading ? "Pepper is working…" : "Research & write  →"}
            </Text>
          </Pressable>
        </View>
      </View>

      {(status === "RECEIVED" || status === "GENERATING") && (
        <View style={[styles.progress, { borderColor: e.border, backgroundColor: e.surface }]}>
          <Text style={[styles.progressLabel, { color: e.teal }]}>
            {status === "RECEIVED" ? "REQUEST RECEIVED" : "RESEARCHING AND WRITING"}
          </Text>
          <Text style={[styles.progressCopy, { color: e.secondary }]}>
            Keep this screen open for live progress. Pepper will continue if the app refreshes.
          </Text>
        </View>
      )}

      {error && <Text style={[styles.error, { color: e.coral }]}>{error}</Text>}

      {content && (
        <View style={styles.draftFields}>
          <Text style={[styles.label, { color: e.muted }]}>EDIT PEPPER&apos;S DRAFT</Text>
          <Text style={[styles.fieldLabel, { color: e.ink }]}>Support question</Text>
          <TextInput
            accessibilityLabel="Support question"
            value={content.supportQuestion}
            onChangeText={(supportQuestion) => update({ ...content, supportQuestion })}
            multiline
            maxLength={512}
            style={[styles.questionInput, { color: e.onInkBlock, backgroundColor: e.inkBlock }]}
          />

          <Text style={[styles.fieldLabel, { color: e.ink }]}>Post summary</Text>
          <TextInput
            accessibilityLabel="Post summary"
            value={content.summary}
            onChangeText={(summary) => update({ ...content, summary })}
            multiline
            maxLength={4000}
            style={[styles.draftInput, styles.summaryInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]}
          />

          <Text style={[styles.fieldLabel, { color: e.ink }]}>Case for</Text>
          <TextInput
            accessibilityLabel="Case for"
            value={content.caseFor ?? ""}
            onChangeText={(caseFor) => update({ ...content, caseFor: caseFor || null })}
            maxLength={512}
            style={[styles.draftInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]}
          />

          <Text style={[styles.fieldLabel, { color: e.ink }]}>Case against</Text>
          <TextInput
            accessibilityLabel="Case against"
            value={content.caseAgainst ?? ""}
            onChangeText={(caseAgainst) => update({ ...content, caseAgainst: caseAgainst || null })}
            maxLength={512}
            style={[styles.draftInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]}
          />

          <View style={styles.votingHeader}>
            <Text style={[styles.fieldLabel, { color: e.ink }]}>Voting options</Text>
            <Pressable
              accessibilityRole="switch"
              accessibilityLabel="Multiple choice"
              accessibilityState={{ checked: content.votingType === "MULTIPLE_CHOICE" }}
              onPress={() => update(content.votingType === "BINARY"
                ? { ...content, votingType: "MULTIPLE_CHOICE", voteOptions: ["", ""] }
                : { ...content, votingType: "BINARY", voteOptions: ["Agree", "Disagree"] })}
            >
              <Text style={[styles.switchText, { color: e.teal }]}>
                {content.votingType === "MULTIPLE_CHOICE" ? "Multiple choice" : "Agree / Disagree"}
              </Text>
            </Pressable>
          </View>
          {content.votingType === "BINARY" ? (
            <View style={styles.voteRow}>
              {content.voteOptions.map((option, index) => (
                <View key={`${option}-${index}`} style={[styles.votePill, { borderColor: index === 0 ? e.teal : e.coral }]}>
                  <Text style={[styles.votePillText, { color: index === 0 ? e.teal : e.coral }]}>{option}</Text>
                </View>
              ))}
            </View>
          ) : (
            <View style={styles.optionList}>
              {content.voteOptions.map((option, index) => (
                <View key={index} style={styles.optionRow}>
                  <TextInput
                    accessibilityLabel={`Choice ${index + 1}`}
                    value={option}
                    onChangeText={(value) => update({
                      ...content,
                      voteOptions: content.voteOptions.map((item, itemIndex) => itemIndex === index ? value : item),
                    })}
                    maxLength={120}
                    style={[styles.draftInput, styles.optionInput, { color: e.ink, borderColor: e.border, backgroundColor: e.surface }]}
                  />
                  {content.voteOptions.length > 2 && (
                    <Pressable
                      accessibilityRole="button"
                      accessibilityLabel={`Remove choice ${index + 1}`}
                      onPress={() => update({
                        ...content,
                        voteOptions: content.voteOptions.filter((_, itemIndex) => itemIndex !== index),
                      })}
                    >
                      <Text style={[styles.remove, { color: e.coral }]}>×</Text>
                    </Pressable>
                  )}
                </View>
              ))}
              {content.voteOptions.length < 5 && (
                <Pressable accessibilityRole="button" accessibilityLabel="Add option"
                  onPress={() => update({ ...content, voteOptions: [...content.voteOptions, ""] })}>
                  <Text style={[styles.add, { color: e.teal }]}>+ Add option</Text>
                </Pressable>
              )}
            </View>
          )}

          <Text style={[styles.label, styles.citationHeading, { color: e.muted }]}>CITATIONS</Text>
          {content.citations.length === 0 ? (
            <Text style={[styles.progressCopy, { color: e.muted }]}>No citations selected.</Text>
          ) : content.citations.map((citation) => (
            <View key={citation.url} style={[styles.citation, { borderColor: e.border }]}>
              <View style={styles.citationCopy}>
                <Text style={[styles.citationTitle, { color: e.ink }]}>{citation.title}</Text>
                <Text style={[styles.citationPublisher, { color: e.muted }]}>{citation.publisher}</Text>
              </View>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Remove citation ${citation.title}`}
                onPress={() => update({
                  ...content,
                  citations: content.citations.filter((item) => item.url !== citation.url),
                })}
              >
                <Text style={[styles.removeCitation, { color: e.coral }]}>Remove</Text>
              </Pressable>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 8 },
  label: { fontFamily: EditorialFont.mono, fontSize: 10, letterSpacing: 1.4 },
  promptBox: { borderRadius: 14, borderWidth: 1.5, padding: 15 },
  promptIntro: { flexDirection: "row", alignItems: "flex-start", gap: 9, marginBottom: 12 },
  avatar: { width: 26, height: 26, borderRadius: 8, alignItems: "center", justifyContent: "center" },
  avatarGlyph: { fontSize: 13 },
  introText: { flex: 1, fontFamily: EditorialFont.serifRegular, fontSize: 16, lineHeight: 22 },
  promptInput: { minHeight: 96, borderRadius: 11, borderWidth: 1, padding: 12, fontFamily: EditorialFont.sans, fontSize: 15, lineHeight: 22, textAlignVertical: "top" },
  promptInputFocused: { minHeight: 420 },
  promptFooter: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 11 },
  footerNote: { fontFamily: EditorialFont.mono, fontSize: 9, letterSpacing: 0.8 },
  cta: { minHeight: 38, paddingHorizontal: 15, borderRadius: 10, alignItems: "center", justifyContent: "center" },
  ctaText: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 12.5 },
  progress: { borderWidth: 1, borderRadius: 12, padding: 13, gap: 5 },
  progressLabel: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10, letterSpacing: 0.9 },
  progressCopy: { fontFamily: EditorialFont.sans, fontSize: 12, lineHeight: 18 },
  error: { fontFamily: EditorialFont.sansBold, fontSize: 13, lineHeight: 19 },
  draftFields: { gap: 8, marginTop: 12 },
  fieldLabel: { fontFamily: EditorialFont.sansBold, fontWeight: "700", fontSize: 14, marginTop: 7 },
  questionInput: { minHeight: 88, borderRadius: 14, padding: 15, fontFamily: EditorialFont.serifItalic, fontStyle: "italic", fontSize: 20, lineHeight: 27, textAlignVertical: "top" },
  draftInput: { minHeight: 48, borderWidth: 1.5, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, fontFamily: EditorialFont.sans, fontSize: 14, textAlignVertical: "top" },
  summaryInput: { minHeight: 140 },
  votingHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  switchText: { fontFamily: EditorialFont.monoSemiBold, fontSize: 10 },
  voteRow: { flexDirection: "row", gap: 8 },
  votePill: { flex: 1, height: 34, borderRadius: 9, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  votePillText: { fontFamily: EditorialFont.mono, fontSize: 10, textTransform: "uppercase", letterSpacing: 0.8 },
  optionList: { gap: 8 },
  optionRow: { flexDirection: "row", alignItems: "center", gap: 9 },
  optionInput: { flex: 1 },
  remove: { fontFamily: EditorialFont.sansBold, fontSize: 22 },
  add: { fontFamily: EditorialFont.sansBold, fontSize: 13, paddingVertical: 6 },
  citationHeading: { marginTop: 12 },
  citation: { flexDirection: "row", alignItems: "center", gap: 12, borderTopWidth: 1, paddingVertical: 10 },
  citationCopy: { flex: 1 },
  citationTitle: { fontFamily: EditorialFont.serifRegular, fontSize: 15 },
  citationPublisher: { fontFamily: EditorialFont.mono, fontSize: 9, marginTop: 3 },
  removeCitation: { fontFamily: EditorialFont.sansBold, fontSize: 12 },
});
