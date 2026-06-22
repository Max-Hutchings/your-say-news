import React, { useState } from "react";
import { View, Text, StyleSheet, ScrollView, KeyboardAvoidingView, Platform } from "react-native";
import { useRouter } from "expo-router";
import { Button, Input } from "@/components/ui";
import { ThemedView } from "@/components/themed-view";
import { ThemedText } from "@/components/themed-text";
import { useTheme, Spacing, Typography } from "@/constants/theme";
import { useCreatePost } from "../hooks/use-create-post";
import { MediaPicker } from "./MediaPicker";

/**
 * The full create-post experience: headline, summary and support-question
 * fields, optional media, and publish. All orchestration lives in
 * useCreatePost; this composes the UI and navigates home on success.
 */
export function CreatePostScreen() {
  const router = useRouter();
  const { colors } = useTheme();
  const {
    picked,
    progress,
    submitting,
    error,
    fieldErrors,
    pickMedia,
    clearMedia,
    submit,
  } = useCreatePost();

  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [supportQuestion, setSupportQuestion] = useState("");

  const handlePublish = async () => {
    const created = await submit({ title, summary, supportQuestion });
    if (created) router.back();
  };

  return (
    <ThemedView style={styles.container}>
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
          <View style={styles.header}>
            <ThemedText variant="h3">Share a story</ThemedText>
            <ThemedText variant="bodySmall" color="secondary">
              Add a headline, a summary and the question you want people to weigh in on.
            </ThemedText>
          </View>

          <Input
            label="Headline"
            placeholder="A clear, specific headline"
            value={title}
            onChangeText={setTitle}
            error={fieldErrors.title}
          />

          <Input
            label="Summary"
            placeholder="Give readers the context they need before voting"
            value={summary}
            onChangeText={setSummary}
            error={fieldErrors.summary}
            multiline
            inputStyle={styles.multiline}
          />

          <Input
            label="Support question"
            placeholder="Do you agree that ...?"
            value={supportQuestion}
            onChangeText={setSupportQuestion}
            error={fieldErrors.supportQuestion}
          />

          <View style={styles.mediaSection}>
            <Text style={[styles.mediaLabel, { color: colors.text.secondary }]}>
              Media (optional)
            </Text>
            <MediaPicker
              media={picked}
              progress={progress}
              uploading={submitting && Boolean(picked)}
              onPick={pickMedia}
              onClear={clearMedia}
            />
          </View>

          {error && (
            <Text style={[styles.error, { color: colors.status.error }]}>{error}</Text>
          )}

          <Button fullWidth loading={submitting} onPress={handlePublish}>
            Publish
          </Button>
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  content: {
    padding: Spacing.base,
    gap: Spacing.base,
  },
  header: {
    gap: Spacing.xs,
    marginBottom: Spacing.sm,
  },
  multiline: {
    height: 120,
    paddingTop: Spacing.md,
    textAlignVertical: "top",
  },
  mediaSection: {
    gap: Spacing.xs,
  },
  mediaLabel: {
    ...Typography.labelMedium,
  },
  error: {
    ...Typography.bodySmall,
  },
});
