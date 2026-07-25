import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { PanResponder, View } from "react-native";
import { ThemeProvider } from "@/constants/theme";
import { NewsSourceSlider } from "./NewsSourceSlider";

beforeAll(() => {
  jest.spyOn(PanResponder, "create").mockImplementation((handlers) => {
    return {
      panHandlers: {
        onResponderGrant: handlers.onPanResponderGrant,
        onResponderMove: handlers.onPanResponderMove,
      },
    } as ReturnType<typeof PanResponder.create>;
  });
});

afterAll(() => jest.restoreAllMocks());

function renderSlider(value: number, onChange = jest.fn()) {
  const view = render(
    <ThemeProvider>
      <NewsSourceSlider value={value} onChange={onChange} />
    </ThemeProvider>,
  );
  const track = view.UNSAFE_getAllByType(View).find(
    (node) => typeof node.props.onLayout === "function",
  );
  if (!track) throw new Error("slider track not found");
  return { track, onChange };
}

test("shows complementary mainstream and social percentages", () => {
  renderSlider(72);
  expect(screen.getByText("72%")).toBeTruthy();
  expect(screen.getByText("28%")).toBeTruthy();
});

test("tap position changes the value after the track is measured", () => {
  const { track, onChange } = renderSlider(50);
  fireEvent(track, "layout", { nativeEvent: { layout: { width: 200 } } });

  fireEvent(track, "responderGrant", {
    nativeEvent: { locationX: 50, pageX: 50 },
  });

  expect(onChange).toHaveBeenCalledWith(25);
});

test("dragging uses the grant value and clamps changes to the slider range", () => {
  const { track, onChange } = renderSlider(15);
  fireEvent(track, "layout", { nativeEvent: { layout: { width: 100 } } });
  fireEvent(track, "responderGrant", {
    nativeEvent: { locationX: 40, pageX: 40 },
  });
  fireEvent(track, "responderMove", { nativeEvent: { pageX: 125 } });
  fireEvent(track, "responderMove", { nativeEvent: { pageX: -70 } });

  expect(onChange.mock.calls).toEqual([[40], [100], [0]]);
});
