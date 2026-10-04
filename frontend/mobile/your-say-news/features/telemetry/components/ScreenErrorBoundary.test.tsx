import { fireEvent, render, screen } from "@testing-library/react-native";
import { ScreenErrorBoundary } from "./ScreenErrorBoundary";
import { recorder } from "../services/telemetry";
import { SplashScreen } from "expo-router";

jest.mock("expo-router", () => ({ SplashScreen: { hideAsync: jest.fn(() => Promise.resolve()) } }));

describe("ScreenErrorBoundary", () => {
    afterEach(() => jest.restoreAllMocks());

    it("reports the render crash and lets the reader retry", () => {
        const report = jest.spyOn(recorder, "reportError").mockImplementation(() => undefined);
        const retry = jest.fn(() => Promise.resolve());
        const crash = new TypeError("post.voteOptions is undefined");

        render(<ScreenErrorBoundary error={crash} retry={retry} />);
        fireEvent.press(screen.getByRole("button", { name: "Try again" }));

        screen.getByText("This page could not be shown.");
        expect(SplashScreen.hideAsync).toHaveBeenCalledTimes(1);
        expect(report).toHaveBeenCalledWith(crash, "render", false);
        expect(retry).toHaveBeenCalledTimes(1);
    });
});
