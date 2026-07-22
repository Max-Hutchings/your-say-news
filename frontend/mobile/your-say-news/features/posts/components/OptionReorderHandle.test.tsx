import React from "react";
import { PanResponder } from "react-native";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { OptionReorderHandle } from "./OptionReorderHandle";

describe("OptionReorderHandle", () => {
  beforeEach(() => {
    jest.spyOn(PanResponder, "create").mockImplementation((config) => ({
      panHandlers: {
        onResponderGrant: config.onPanResponderGrant,
        onResponderMove: config.onPanResponderMove,
      },
    }) as ReturnType<typeof PanResponder.create>);
  });

  afterEach(() => jest.restoreAllMocks());

  it("moves up once when a drag crosses the upward row threshold", () => {
    const onMoveUp = jest.fn();
    const onMoveDown = jest.fn();
    render(<OptionReorderHandle color="#000" canMoveUp canMoveDown
      onMoveUp={onMoveUp} onMoveDown={onMoveDown} />);

    const handle = screen.getByTestId("option-drag-handle");
    fireEvent(handle, "responderGrant");
    fireEvent(handle, "responderMove", {}, { dy: -30 });
    fireEvent(handle, "responderMove", {}, { dy: -60 });

    expect(onMoveUp).toHaveBeenCalledTimes(1);
    expect(onMoveDown).not.toHaveBeenCalled();
  });

  it("does not move beyond an unavailable boundary", () => {
    const onMoveUp = jest.fn();
    const onMoveDown = jest.fn();
    render(<OptionReorderHandle color="#000" canMoveUp={false} canMoveDown
      onMoveUp={onMoveUp} onMoveDown={onMoveDown} />);

    const handle = screen.getByTestId("option-drag-handle");
    fireEvent(handle, "responderGrant");
    fireEvent(handle, "responderMove", {}, { dy: -50 });

    expect(onMoveUp).not.toHaveBeenCalled();
    expect(onMoveDown).not.toHaveBeenCalled();
  });
});
