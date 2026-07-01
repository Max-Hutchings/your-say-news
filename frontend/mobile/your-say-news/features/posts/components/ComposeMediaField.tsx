import React, { useState } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { Eyebrow } from "@/components/ui";
import type { LocalMedia } from "../services/MediaUploadService";

/**
 * The composer's media well (design handoff): a segmented Image / Video control
 * over a single dashed dropzone whose copy and limits swap with the tab — never
 * two competing uploaders. Once an asset is picked it becomes a framed preview
 * with a remove control and an upload progress bar.
 *
 * The pick/upload itself lives in the create-post hook; this is the surface,
 * wired to that hook's pickMedia / clearMedia.
 */

type Tab = "image" | "video";

const COPY: Record<Tab, { title: string; limits: string }> = {
  image: { title: "Add a cover photo", limits: "JPG or PNG · up to 12 MB · 16:9 looks best" },
  video: { title: "Record or upload a clip", limits: "MP4 or MOV · up to 60 s · 200 MB" },
};

export function ComposeMediaField({
  media,
  progress,
  uploading,
  onPick,
  onClear,
}: {
  media: LocalMedia | null;
  progress: number;
  uploading: boolean;
  onPick: () => void;
  onClear: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [tab, setTab] = useState<Tab>("image");

  const pct = Math.round(Math.min(1, progress) * 100);

  return (
    <View>
      <View style={styles.labelRow}>
        <Eyebrow text="MEDIA " />
        <Eyebrow text="· OPTIONAL" style={{ color: e.chipText }} />
      </View>

      {!media && (
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
            onPress={onPick}
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

      {media && (
        <View style={[styles.preview, { backgroundColor: e.surfaceAlt }]}>
          {media.mediaType === "IMAGE" ? (
            <Image source={{ uri: media.uri }} style={StyleSheet.absoluteFill} contentFit="cover" />
          ) : (
            <View style={[StyleSheet.absoluteFill, styles.videoFill, { backgroundColor: e.ink }]}>
              <View style={[styles.play, { backgroundColor: e.lime }]}>
                <Text style={[styles.playIcon, { color: e.onLime }]}>▶</Text>
              </View>
            </View>
          )}

          <Pressable
            onPress={onClear}
            accessibilityRole="button"
            accessibilityLabel="Remove media"
            style={[styles.remove, { backgroundColor: e.mediaScrim }]}
          >
            <Text style={[styles.removeIcon, { color: e.onMedia }]}>×</Text>
          </Pressable>

          <View style={styles.previewFooter}>
            <Text style={[styles.previewLabel, { color: e.onMedia }]}>
              {media.mediaType === "IMAGE" ? "IMAGE" : "VIDEO"}
            </Text>
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
  preview: {
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
