import React, { useEffect, useState } from "react";
import { View, Text, Pressable, StyleSheet } from "react-native";
import { Image } from "expo-image";
import { useVideoPlayer, VideoView } from "expo-video";
import { getEditorial, useTheme, EditorialFont } from "@/constants/theme";

/**
 * A post's video in the immersive feed. It autoplays (muted, looping) as soon as
 * its post is the on-screen one and pauses the moment it scrolls away — driven by
 * the `isActive` flag the feed derives from viewability. Muted autoplay is the
 * reliable cross-platform behaviour; a tap toggles sound.
 *
 * Playback is only started once the player reports `readyToPlay` — calling play()
 * on a source that is still loading or has failed (e.g. a 404) rejects on web and
 * would surface as an unhandled error. Until then (and on error) we show the
 * poster frame, so a missing clip degrades to a still rather than a crash.
 */
export function PostVideo({
  uri,
  posterUri,
  isActive,
  width,
  height,
  controlsBottomInset = 12,
}: {
  uri: string;
  posterUri?: string | null;
  isActive: boolean;
  width: number;
  height: number;
  /** Raises the mute control off the bottom edge — used by the immersive layout so it clears the vote bar. */
  controlsBottomInset?: number;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [muted, setMuted] = useState(true);

  const player = useVideoPlayer(uri, (p) => {
    p.loop = true;
    p.muted = true;
  });

  const [status, setStatus] = useState(player.status);

  useEffect(() => {
    const sub = player.addListener("statusChange", (payload) => setStatus(payload.status));
    return () => sub.remove();
  }, [player]);

  // Play only while this post is the active one AND the source is ready; pause otherwise.
  useEffect(() => {
    if (isActive && status === "readyToPlay") {
      player.play();
    } else {
      player.pause();
    }
  }, [isActive, status, player]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/immutability
    player.muted = muted;
  }, [muted, player]);

  const showPoster = status !== "readyToPlay";

  return (
    <View style={{ width, height }}>
      <VideoView
        player={player}
        style={styles.fill}
        contentFit="cover"
        nativeControls={false}
        testID="post-card-video"
      />

      {showPoster && posterUri ? (
        <Image source={{ uri: posterUri }} style={StyleSheet.absoluteFill} contentFit="cover" />
      ) : null}

      {status === "error" && (
        <View style={[styles.unavailable, { backgroundColor: e.ink }]}>
          <Text style={[styles.unavailableText, { color: e.onMedia }]}>Video unavailable</Text>
        </View>
      )}

      <Pressable
        onPress={() => setMuted((m) => !m)}
        accessibilityRole="button"
        accessibilityLabel={muted ? "Unmute video" : "Mute video"}
        style={[styles.mute, { bottom: controlsBottomInset, backgroundColor: e.mediaScrim }]}
      >
        <Text style={[styles.muteIcon, { color: e.onMedia }]}>{muted ? "🔇" : "🔊"}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  // An explicit 100%×100% box (not absoluteFill): on web the <video> is a replaced element, and an
  // absolutely-positioned one keeps its intrinsic 16:9 size, so `contentFit: cover` never fills the
  // box and the clip letterboxes. A concrete size lets object-fit: cover crop-to-fill the media cell.
  fill: {
    width: "100%",
    height: "100%",
  },
  unavailable: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: "center",
    justifyContent: "center",
    opacity: 0.85,
  },
  unavailableText: {
    fontFamily: EditorialFont.mono,
    fontSize: 11,
    letterSpacing: 0.8,
  },
  mute: {
    position: "absolute",
    right: 12,
    bottom: 12,
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: "center",
    justifyContent: "center",
  },
  muteIcon: {
    fontFamily: EditorialFont.sans,
    fontSize: 15,
  },
});
