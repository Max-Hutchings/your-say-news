import { Platform } from "react-native";
import type { TelemetryRecorder } from "./TelemetryRecorder";

type GlobalHandler = (error: unknown, isFatal?: boolean) => void;

interface ErrorUtilsLike {
    getGlobalHandler(): GlobalHandler;
    setGlobalHandler(handler: GlobalHandler): void;
}

/**
 * Reports JavaScript errors no error boundary caught. On native this chains React Native's global
 * handler (keeping its red box and crash behaviour); on web it listens to window errors and
 * unhandled promise rejections.
 */
export function installCrashReporting(recorder: TelemetryRecorder): void {
    if (Platform.OS === "web") {
        installWebHandlers(recorder);
        return;
    }
    const errorUtils = (globalThis as { ErrorUtils?: ErrorUtilsLike }).ErrorUtils;
    if (!errorUtils) {
        return;
    }
    const previous = errorUtils.getGlobalHandler();
    errorUtils.setGlobalHandler((error, isFatal) => {
        recorder.reportError(error, "global", Boolean(isFatal));
        previous(error, isFatal);
    });
}

function installWebHandlers(recorder: TelemetryRecorder): void {
    if (typeof window === "undefined" || !window.addEventListener) {
        return;
    }
    window.addEventListener("error", (event) => recorder.reportError(event.error ?? event.message, "global", false));
    window.addEventListener("unhandledrejection", (event) => recorder.reportError(event.reason, "global", false));
}
