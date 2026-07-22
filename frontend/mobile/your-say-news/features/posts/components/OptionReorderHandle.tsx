import React, { useMemo, useRef } from "react";
import { PanResponder, View } from "react-native";
import { Ionicons } from "@expo/vector-icons";

const MOVE_THRESHOLD = 28;

/** Drag handle that moves an option one place once the gesture crosses the adjacent-row threshold. */
export function OptionReorderHandle({ color, canMoveUp, canMoveDown, onMoveUp, onMoveDown }: {
  color: string;
  canMoveUp: boolean;
  canMoveDown: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
}) {
  const moved = useRef(false);
  const responder = useMemo(() => PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: (_, gesture) => Math.abs(gesture.dy) > 4,
    onPanResponderGrant: () => { moved.current = false; },
    onPanResponderMove: (_, gesture) => {
      if (moved.current) return;
      if (gesture.dy <= -MOVE_THRESHOLD && canMoveUp) {
        moved.current = true;
        onMoveUp();
      } else if (gesture.dy >= MOVE_THRESHOLD && canMoveDown) {
        moved.current = true;
        onMoveDown();
      }
    },
  }), [canMoveDown, canMoveUp, onMoveDown, onMoveUp]);

  return <View testID="option-drag-handle" accessible={false} {...responder.panHandlers}>
    <Ionicons name="reorder-three" size={22} color={color} />
  </View>;
}
