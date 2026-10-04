import { useEffect, useLayoutEffect } from "react";
import { AppState } from "react-native";
import { useGlobalSearchParams, useSegments } from "expo-router";
import { recorder } from "../services/telemetry";
import { screenFromSegments, screenTarget } from "../services/routeTemplate";

/**
 * Records every route change as a screen view, plus app background / foreground. Mount once in the
 * root layout so all routes, including ones added later, are covered without per-screen code.
 *
 * Layout effect on purpose: it runs before child screens' data-fetching effects, so their first
 * API calls already belong to the new screen's trace.
 */
export function useScreenTracking(): void {
    const segments = useSegments();
    const params = useGlobalSearchParams();
    const screen = screenFromSegments(segments);
    const target = screenTarget(params);

    useLayoutEffect(() => {
        recorder.enterScreen(screen, target);
    }, [screen, target]);

    useEffect(() => {
        let backgrounded = false;
        const subscription = AppState.addEventListener("change", (state) => {
            if (state === "background" && !backgrounded) {
                backgrounded = true;
                recorder.appBackgrounded();
            } else if (state === "active" && backgrounded) {
                backgrounded = false;
                recorder.appForegrounded();
            }
        });
        return () => subscription.remove();
    }, []);
}
