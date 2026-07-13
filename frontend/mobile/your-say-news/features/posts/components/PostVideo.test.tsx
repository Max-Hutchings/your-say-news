import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { ThemeProvider } from "@/constants/theme";
import { PostVideo } from "./PostVideo";

// Stub the native player; the setup callback and property writes land on this object.
// status defaults to "readyToPlay" so the active-post path calls play().
const mockPlayer = {
  play: jest.fn(),
  pause: jest.fn(),
  muted: false,
  loop: false,
  currentTime: 0,
  status: "readyToPlay",
  addListener: jest.fn(() => ({ remove: jest.fn() })),
};
const mockIonicons = jest.fn((_props: { name: string }) => null);

jest.mock("expo-video", () => ({
  useVideoPlayer: (_uri: string, setup?: (p: unknown) => void) => {
    setup?.(mockPlayer);
    return mockPlayer;
  },
  VideoView: ({ testID }: { testID?: string }) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports -- jest.mock factories can't close over imports
    const { View } = require("react-native");
    return <View testID={testID} />;
  },
}));
jest.mock("@expo/vector-icons", () => ({
  Ionicons: (props: { name: string }) => {
    mockIonicons(props);
    return null;
  },
}));

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider>{ui}</ThemeProvider>);
}

beforeEach(() => {
  mockPlayer.play.mockClear();
  mockPlayer.pause.mockClear();
  mockIonicons.mockClear();
  mockPlayer.muted = false;
  mockPlayer.currentTime = 5;
});

describe("PostVideo autoplay", () => {
  it("plays while its post is active and ready, and pauses once it isn't", () => {
    const { rerender } = renderWithTheme(
      <PostVideo uri="https://s3.local/clip.mp4" isActive width={200} height={200} />
    );
    expect(mockPlayer.play).toHaveBeenCalledTimes(1);
    expect(mockPlayer.pause).not.toHaveBeenCalled();

    rerender(
      <ThemeProvider>
        <PostVideo uri="https://s3.local/clip.mp4" isActive={false} width={200} height={200} />
      </ThemeProvider>
    );
    expect(mockPlayer.pause).toHaveBeenCalledTimes(1);
  });

  it("starts muted and unmutes when the control is tapped", () => {
    renderWithTheme(
      <PostVideo
        uri="https://s3.local/clip.mp4"
        isActive
        width={200}
        height={200}
        controlsBottomInset={52}
      />
    );
    // The setup + muted effect force it muted regardless of the stub's initial value.
    expect(mockPlayer.muted).toBe(true);
    expect(screen.getByTestId("video-sound-control")).toHaveStyle({ bottom: 52 });
    expect(mockIonicons).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: "volume-mute-outline" })
    );
    expect(screen.queryByText("🔇")).toBeNull();

    fireEvent.press(screen.getByLabelText("Unmute video"));
    expect(mockPlayer.muted).toBe(false);
    expect(screen.getByLabelText("Mute video")).toBeOnTheScreen();
    expect(mockIonicons).toHaveBeenLastCalledWith(
      expect.objectContaining({ name: "volume-high-outline" })
    );
  });
});
