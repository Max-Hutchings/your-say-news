import React, { useState } from "react";
import {
  View,
  Text,
  ScrollView,
  StyleSheet,
  type LayoutChangeEvent,
  type NativeSyntheticEvent,
  type NativeScrollEvent,
} from "react-native";
import { getEditorial, useTheme, EditorialFont } from "@/constants/theme";

/**
 * The post's 2-3 paragraph summary. Because the whole story is shown in the feed
 * (there's no detail screen), the body scrolls within its bounded region — and a
 * persistent scrollbar on the right makes it obvious there's more to read. The
 * scrollbar is a custom track/thumb (RN's own indicator is transient on iOS), sized
 * and positioned from the content/viewport ratio, and only shown when it overflows.
 */
export function ScrollableSummary({
  text,
  footer,
}: {
  text: string;
  /** Extra content that scrolls beneath the summary (e.g. the case-for/against cards). */
  footer?: React.ReactNode;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [viewportH, setViewportH] = useState(0);
  const [contentH, setContentH] = useState(0);
  const [offsetY, setOffsetY] = useState(0);

  const overflowing = contentH > viewportH + 1;
  const thumbH = overflowing ? Math.max(28, (viewportH / contentH) * viewportH) : 0;
  const maxOffset = Math.max(1, contentH - viewportH);
  const thumbY = overflowing ? (offsetY / maxOffset) * (viewportH - thumbH) : 0;

  const onLayout = (ev: LayoutChangeEvent) => setViewportH(ev.nativeEvent.layout.height);
  const onScroll = (ev: NativeSyntheticEvent<NativeScrollEvent>) =>
    setOffsetY(ev.nativeEvent.contentOffset.y);

  return (
    <View style={styles.wrap} onLayout={onLayout}>
      <ScrollView
        onContentSizeChange={(_, h) => setContentH(h)}
        onScroll={onScroll}
        scrollEventThrottle={16}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.content}
      >
        <Text style={[styles.summary, { color: e.secondary }]}>{text}</Text>
        {footer}
      </ScrollView>

      {overflowing && (
        <View style={[styles.track, { backgroundColor: e.border }]} pointerEvents="none">
          <View
            testID="summary-scrollbar-thumb"
            style={[
              styles.thumb,
              { height: thumbH, transform: [{ translateY: thumbY }], backgroundColor: e.muted },
            ]}
          />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    position: "relative",
  },
  content: {
    paddingRight: 16,
    paddingBottom: 4,
  },
  summary: {
    fontFamily: EditorialFont.serifRegular,
    fontSize: 16,
    lineHeight: 25,
  },
  track: {
    position: "absolute",
    top: 0,
    bottom: 0,
    right: 0,
    width: 3,
    borderRadius: 2,
  },
  thumb: {
    position: "absolute",
    top: 0,
    right: 0,
    width: 3,
    borderRadius: 2,
  },
});
