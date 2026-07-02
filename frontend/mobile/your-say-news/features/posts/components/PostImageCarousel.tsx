import React, { useState } from "react";
import { View, FlatList, StyleSheet, type NativeSyntheticEvent, type NativeScrollEvent } from "react-native";
import { Image } from "expo-image";
import { getEditorial, useTheme } from "@/constants/theme";
import type { PostMedia } from "../types";

/**
 * A post's images in the immersive feed. One image fills the media region; two
 * or more become a horizontally swipeable carousel with dot indicators so it's
 * obvious there's more than one (up to five per the post-service limit).
 */
export function PostImageCarousel({
  images,
  width,
  height,
}: {
  images: PostMedia[];
  width: number;
  height: number;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);
  const [index, setIndex] = useState(0);

  const onScrollEnd = (ev: NativeSyntheticEvent<NativeScrollEvent>) => {
    setIndex(Math.round(ev.nativeEvent.contentOffset.x / width));
  };

  return (
    <View style={{ width, height }}>
      <FlatList
        data={images}
        keyExtractor={(m, i) => `${m.s3Key}-${i}`}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onMomentumScrollEnd={onScrollEnd}
        getItemLayout={(_, i) => ({ length: width, offset: width * i, index: i })}
        renderItem={({ item }) => (
          <Image
            source={{ uri: item.url }}
            style={{ width, height }}
            contentFit="cover"
            testID="post-card-media"
          />
        )}
      />

      {images.length > 1 && (
        <View style={styles.dots} pointerEvents="none">
          {images.map((m, i) => (
            <View
              key={`${m.s3Key}-dot-${i}`}
              style={[
                styles.dot,
                { backgroundColor: i === index ? e.onMedia : "rgba(255,255,255,0.45)" },
                i === index && styles.dotActive,
              ]}
            />
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  dots: {
    position: "absolute",
    bottom: 12,
    left: 0,
    right: 0,
    flexDirection: "row",
    justifyContent: "center",
    gap: 6,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  dotActive: {
    width: 18,
  },
});
