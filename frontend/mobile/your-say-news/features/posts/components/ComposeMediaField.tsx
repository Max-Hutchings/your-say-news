import React, { useState } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { Eyebrow } from "@/components/ui";
import type { LocalMedia } from "../services/MediaUploadService";
import type { MediaType } from "../types";
import { MAX_IMAGES } from "../hooks/use-create-post";

/**
 * The composer's media well (design handoff). A post carries EITHER up to five
 * images — shown here as a thumbnail row with an "add" tile — OR a single video.
 * When nothing is picked yet, a segmented Image / Video control over a dashed
 * dropzone lets the author choose which to add; the copy and limits swap with
 * the tab so there are never two competing uploaders.
 *
 * The pick/upload itself lives in the create-post hook; this is the surface,
 * wired to that hook's pickMedia(kind) / removeMedia(index).
 */

type Tab = "image" | "video";

const COPY: Record<Tab, { title: string; limits: string }> = {
  image: { title: "Add up to 5 photos", limits: "JPG or PNG · up to 12 MB each · 16:9 looks best" },
  video: { title: "Record or upload a clip", limits: "MP4 or MOV · up to 60 s · 200 MB" },
};

export function ComposeMediaField({
  media,
  progress,
  uploading,
  onPick,
  onRemove,
}: {
  media: LocalMedia[];
  progress: number;
  uploading: boolean;
  onPick: (kind: MediaType) => void;
  onRemove: (index: number) => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [tab, setTab] = useState<Tab>("image");

  const pct = Math.round(Math.min(1, progress) * 100);
  const isVideo = media[0]?.mediaType === "VIDEO";
  const imageCount = media.filter((m) => m.mediaType === "IMAGE").length;
  const canAddImage = !isVideo && imageCount < MAX_IMAGES;

  return (
    <View>
      <View style={styles.labelRow}>
        <Eyebrow text="MEDIA " />
        <Eyebrow text={media.length > 0 && !isVideo ? `· ${imageCount}/${MAX_IMAGES}` : "· OPTIONAL"} style={{ color: e.chipText }} />
      </View>

      {media.length === 0 && (
        <>
          <View style={[styles.tabTrack, { backgroundColor: e.track }]}>
            {(["image", "video"] as Tab[]).map((t) => {
              const active = tab === t;
              return (
                <Pressable
                  key={t}
                  onPress={() => setTab(t)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={[styles.tab, active && { backgroundColor: e.ink }]}
                >
                  <Text style={[styles.tabText, { color: active ? e.bg : e.secondary }]}>
                    {t === "image" ? "Image" : "Video"}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <Pressable
            onPress={() => onPick(tab === "image" ? "IMAGE" : "VIDEO")}
            accessibilityRole="button"
            accessibilityLabel={COPY[tab].title}
            style={[styles.dropzone, { borderColor: e.chipText, backgroundColor: e.surface }]}
          >
            <View style={[styles.glyphSquare, { backgroundColor: e.ink }]}>
              <Text style={[styles.glyph, { color: e.lime }]}>{tab === "image" ? "▤" : "▶"}</Text>
            </View>
            <Text style={[styles.dropTitle, { color: e.ink }]}>{COPY[tab].title}</Text>
            <Text style={[styles.dropLimits, { color: e.muted }]}>{COPY[tab].limits}</Text>
          </Pressable>
        </>
      )}

      {isVideo && (
        <View style={[styles.videoPreview, { backgroundColor: e.surfaceAlt }]}>
          <View style={[StyleSheet.absoluteFill, styles.videoFill, { backgroundColor: e.ink }]}>
            <View style={[styles.play, { backgroundColor: e.lime }]}>
              <Text style={[styles.playIcon, { color: e.onLime }]}>▶</Text>
            </View>
          </View>
          <Pressable
            onPress={() => onRemove(0)}
            accessibilityRole="button"
            accessibilityLabel="Remove video"
            style={[styles.remove, { backgroundColor: e.mediaScrim }]}
          >
            <Text style={[styles.removeIcon, { color: e.onMedia }]}>×</Text>
          </Pressable>
          <View style={styles.previewFooter}>
            <Text style={[styles.previewLabel, { color: e.onMedia }]}>VIDEO</Text>
          </View>
          {uploading && (
            <View style={[styles.progressTrack, { backgroundColor: "rgba(0,0,0,0.35)" }]}>
              <View
                testID="media-upload-progress-fill"
                style={[styles.progressFill, { backgroundColor: e.lime, width: `${pct}%` }]}
              />
            </View>
          )}
        </View>
      )}

      {!isVideo && imageCount > 0 && (
        <View style={styles.thumbRow}>
          {media.map((m, index) => (
            <View key={`${m.uri}-${index}`} style={[styles.thumb, { backgroundColor: e.surfaceAlt, borderColor: e.border }]}>
              <Image source={{ uri: m.uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
              <Pressable
                onPress={() => onRemove(index)}
                accessibilityRole="button"
                accessibilityLabel={`Remove image ${index + 1}`}
                style={[styles.thumbRemove, { backgroundColor: e.mediaScrim }]}
              >
                <Text style={[styles.thumbRemoveIcon, { color: e.onMedia }]}>×</Text>
              </Pressable>
            </View>
          ))}
          {canAddImage && (
            <Pressable
              onPress={() => onPick("IMAGE")}
              accessibilityRole="button"
              accessibilityLabel="Add another photo"
              style={[styles.addTile, { borderColor: e.chipText }]}
            >
              <Text style={[styles.addGlyph, { color: e.ink }]}>＋</Text>
            </Pressable>
          )}
          {uploading && (
            <View style={styles.thumbProgressWrap}>
              <View style={[styles.thumbProgressTrack, { backgroundColor: e.track }]}>
                <View
                  testID="media-upload-progress-fill"
                  style={[styles.progressFill, { backgroundColor: e.lime, width: `${pct}%` }]}
                />
              </View>
            </View>
          )}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  labelRow: {
    flexDirection: "row",
    marginBottom: 8,
  },
  tabTrack: {
    flexDirection: "row",
    gap: 5,
    borderRadius: 12,
    padding: 4,
    marginBottom: 10,
  },
  tab: {
    flex: 1,
    height: 34,
    borderRadius: 9,
    alignItems: "center",
    justifyContent: "center",
  },
  tabText: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 13,
  },
  dropzone: {
    height: 118,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: "dashed",
    alignItems: "center",
    justifyContent: "center",
    gap: 9,
  },
  glyphSquare: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  glyph: {
    fontSize: 18,
  },
  dropTitle: {
    fontFamily: EditorialFont.sansSemiBold,
    fontWeight: "600",
    fontSize: 14,
  },
  dropLimits: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
  },
  // Image thumbnails
  thumbRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    alignItems: "center",
  },
  thumb: {
    width: 72,
    height: 72,
    borderRadius: 11,
    borderWidth: 1,
    overflow: "hidden",
  },
  thumbRemove: {
    position: "absolute",
    top: 4,
    right: 4,
    width: 22,
    height: 22,
    borderRadius: 7,
    alignItems: "center",
    justifyContent: "center",
  },
  thumbRemoveIcon: {
    fontSize: 14,
    lineHeight: 16,
  },
  addTile: {
    width: 72,
    height: 72,
    borderRadius: 11,
    borderWidth: 1.5,
    borderStyle: "dashed",
    alignItems: "center",
    justifyContent: "center",
  },
  addGlyph: {
    fontSize: 22,
  },
  thumbProgressWrap: {
    width: "100%",
    marginTop: 2,
  },
  thumbProgressTrack: {
    height: 6,
    borderRadius: 3,
    overflow: "hidden",
  },
  // Video preview
  videoPreview: {
    height: 118,
    borderRadius: 14,
    overflow: "hidden",
  },
  videoFill: {
    alignItems: "center",
    justifyContent: "center",
  },
  play: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: "center",
    justifyContent: "center",
  },
  playIcon: {
    fontSize: 16,
    marginLeft: 2,
  },
  remove: {
    position: "absolute",
    top: 9,
    right: 9,
    width: 26,
    height: 26,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  removeIcon: {
    fontSize: 16,
    lineHeight: 18,
  },
  previewFooter: {
    position: "absolute",
    bottom: 9,
    left: 11,
  },
  previewLabel: {
    fontFamily: EditorialFont.mono,
    fontSize: 10,
    letterSpacing: 0.8,
  },
  progressTrack: {
    position: "absolute",
    left: 11,
    right: 11,
    bottom: 30,
    height: 6,
    borderRadius: 3,
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
  },
});
