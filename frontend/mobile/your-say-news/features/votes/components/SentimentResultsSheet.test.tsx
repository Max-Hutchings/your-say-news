import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { SentimentResultsSheet } from "./SentimentResultsSheet";

jest.mock("./SentimentResults", () => {
  const React = jest.requireActual("react");
  const { Pressable, Text } = jest.requireActual("react-native");
  return {
    SentimentResults: ({ onNextPost }: { onNextPost?: () => void }) =>
      React.createElement(
        Pressable,
        { testID: "mock-results-swipe", onPress: onNextPost },
        React.createElement(Text, null, "Mock voting results")
      ),
  };
});

describe("SentimentResultsSheet", () => {
  it("closes the voting data and forwards a completed upward swipe to the feed", () => {
    const onClose = jest.fn();
    const onNextPost = jest.fn();

    render(
      <ThemeProvider>
        <SentimentResultsSheet
          postId={7}
          visible
          onClose={onClose}
          onNextPost={onNextPost}
        />
      </ThemeProvider>
    );

    fireEvent.press(screen.getByTestId("mock-results-swipe"));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onNextPost).toHaveBeenCalledTimes(1);
  });
});
