import React from "react";
import { View, Text, StyleSheet, TouchableOpacity } from "react-native";
import { Image } from "expo-image";
import { useTheme, Spacing, Typography, BorderRadius, BorderWidth } from "@/constants/theme";
import type { LocalMedia } from "../services/MediaUploadService";

/**
 * Lets the author attach one image or video and shows upload progress. The
 * actual pick/upload lives in the create-post hook; this is the presentational
 * surface — an empty drop-zone, a thumbnail preview, and a progress bar while
 * the bytes go to S3.
 */

type Props = {
  media: LocalMedia | null;
  /** 0..1 upload progress; shown while uploading is true. */
  progress: number;
  uploading: boolean;
  onPick: () => void;
  onClear: () => void;
};

export function MediaPicker({ media, progress, uploading, onPick, onClear }: Props) {
  const { colors } = useTheme();

  if (!media) {
    return (
      <TouchableOpacity
        onPress={onPick}
        accessibilityRole="button"
        style={[
          styles.dropzone,
          { borderColor: colors.border.secondary, backgroundColor: colors.surface.secondary },
        ]}
      >
        <Text style={[styles.dropzoneIcon, { color: colors.text.tertiary }]}>＋</Text>
        <Text style={[styles.dropzoneLabel, { color: colors.text.secondary }]}>
          Add a photo or video
        </Text>
      </TouchableOpacity>
    );
  }

  const isVideo = media.mediaType === "VIDEO";

  return (
    <View style={styles.previewWrapper}>
      {isVideo ? (
        <View style={[styles.videoPlaceholder, { backgroundColor: colors.surface.tertiary }]}>
          <Text style={[styles.dropzoneIcon, { color: colors.text.tertiary }]}>🎬</Text>
          <Text style={[styles.dropzoneLabel, { color: colors.text.secondary }]}>
            Video attached
          </Text>
        </View>
      ) : (
        <Image source={{ uri: media.uri }} style={styles.preview} contentFit="cover" />
      )}

      {uploading ? (
        <View style={styles.progressRow}>
          <View style={[styles.progressTrack, { backgroundColor: colors.surface.tertiary }]}>
            <View
              testID="media-upload-progress-fill"
              style={[
                styles.progressFill,
                {
                  backgroundColor: colors.brand.primary,
                  width: `${Math.round(Math.min(1, progress) * 100)}%`,
                },
              ]}
            />
          </View>
          <Text style={[styles.progressText, { color: colors.text.tertiary }]}>
            {Math.round(Math.min(1, progress) * 100)}%
          </Text>
        </View>
      ) : (
        <TouchableOpacity onPress={onClear} accessibilityRole="button">
          <Text style={[styles.removeLabel, { color: colors.status.error }]}>Remove media</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  dropzone: {
    height: 160,
    alignItems: "center",
    justifyContent: "center",
    gap: Spacing.sm,
    borderRadius: BorderRadius.lg,
    borderWidth: BorderWidth.thin,
    borderStyle: "dashed",
  },
  dropzoneIcon: {
    ...Typography.h3,
  },
  dropzoneLabel: {
    ...Typography.labelMedium,
  },
  previewWrapper: {
    gap: Spacing.sm,
  },
  preview: {
    width: "100%",
    height: 200,
    borderRadius: BorderRadius.lg,
  },
  videoPlaceholder: {
    width: "100%",
    height: 200,
    borderRadius: BorderRadius.lg,
    alignItems: "center",
    justifyContent: "center",
    gap: Spacing.sm,
  },
  progressRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: Spacing.sm,
  },
  progressTrack: {
    flex: 1,
    height: 6,
    borderRadius: BorderRadius.full,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
  },
  progressText: {
    ...Typography.caption,
    minWidth: 36,
    textAlign: "right",
  },
  removeLabel: {
    ...Typography.labelMedium,
  },
});
