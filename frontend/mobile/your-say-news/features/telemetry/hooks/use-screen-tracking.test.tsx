import { renderHook } from "@testing-library/react-native";
import { AppState, type AppStateStatus } from "react-native";
import { useScreenTracking } from "./use-screen-tracking";
import { recorder } from "../services/telemetry";

let mockSegments: string[] = [];
let mockParams: Record<string, string> = {};

jest.mock("expo-router", () => ({
    useSegments: () => mockSegments,
    useGlobalSearchParams: () => mockParams,
}));

describe("useScreenTracking", () => {
    let appStateListener: (state: AppStateStatus) => void;

    beforeEach(() => {
        jest.spyOn(recorder, "enterScreen").mockImplementation(() => undefined);
        jest.spyOn(recorder, "appBackgrounded").mockImplementation(() => undefined);
        jest.spyOn(recorder, "appForegrounded").mockImplementation(() => undefined);
        jest.spyOn(AppState, "addEventListener").mockImplementation((_, listener) => {
            appStateListener = listener as (state: AppStateStatus) => void;
            return { remove: jest.fn() } as ReturnType<typeof AppState.addEventListener>;
        });
    });

    afterEach(() => jest.restoreAllMocks());

    it("records each route change as the route template with its story id", () => {
        mockSegments = ["(protected)"];
        mockParams = {};
        const { rerender } = renderHook(() => useScreenTracking());

        mockSegments = ["(protected)", "posts", "[postId]"];
        mockParams = { postId: "42" };
        rerender({});

        expect(recorder.enterScreen).toHaveBeenNthCalledWith(1, "/", undefined);
        expect(recorder.enterScreen).toHaveBeenNthCalledWith(2, "/posts/[postId]", "42");
    });

    it("reports background once and foreground only after a background", () => {
        mockSegments = [];
        renderHook(() => useScreenTracking());

        appStateListener("active");
        appStateListener("inactive");
        appStateListener("background");
        appStateListener("background");
        appStateListener("active");

        expect(recorder.appBackgrounded).toHaveBeenCalledTimes(1);
        expect(recorder.appForegrounded).toHaveBeenCalledTimes(1);
    });
});
