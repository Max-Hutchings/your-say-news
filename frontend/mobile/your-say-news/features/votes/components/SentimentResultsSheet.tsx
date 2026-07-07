import React from "react";
import { View, Text, Pressable, Modal, StyleSheet } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { useTheme, getEditorial, EditorialFont } from "@/constants/theme";
import { SentimentResults } from "./SentimentResults";

/**
 * Bottom-sheet host for {@link SentimentResults} — slides up over the immersive full-screen post
 * (matching the app's other sheets), with a grab handle, a title and a close button. Mounted only
 * while `visible`, so results are fetched fresh each time it opens.
 */
export function SentimentResultsSheet({
  postId,
  visible,
  onClose,
}: {
  postId: number;
  visible: boolean;
  onClose: () => void;
}) {
  const { isDark } = useTheme();
  const e = getEditorial(isDark);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={[styles.backdrop, { backgroundColor: e.mediaScrim }]} onPress={onClose}>
        <Pressable
          style={[styles.sheet, { backgroundColor: e.bg, borderColor: e.border }]}
          onPress={(ev) => ev.stopPropagation()}
        >
          <View style={styles.header}>
            <View style={[styles.handle, { backgroundColor: e.border }]} />
            <View style={styles.headerRow}>
              <Text style={[styles.title, { color: e.ink }]}>How others voted</Text>
              <Pressable
                testID="sentiment-close"
                accessibilityRole="button"
                accessibilityLabel="Close results"
                onPress={onClose}
                hitSlop={10}
              >
                <Ionicons name="close" size={22} color={e.muted} />
              </Pressable>
            </View>
          </View>
          {visible && <SentimentResults postId={postId} />}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    justifyContent: "flex-end",
  },
  sheet: {
    height: "86%",
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderWidth: 1,
    overflow: "hidden",
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  handle: {
    alignSelf: "center",
    width: 40,
    height: 4,
    borderRadius: 2,
    marginBottom: 12,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingBottom: 6,
  },
  title: {
    fontFamily: EditorialFont.serif,
    fontSize: 22,
  },
});
