import AsyncStorage from "@react-native-async-storage/async-storage";
import type { AxiosInstance } from "axios";
import { clientInfo, telemetryUploadUrl } from "./clientInfo";
import { installCrashReporting } from "./crashReporting";
import { instrumentHttpClient } from "./httpInstrumentation";
import { TelemetryRecorder } from "./TelemetryRecorder";
import type { TelemetryAction } from "../vocabulary";

/** One recorder per app launch; its session id ties the journey together. */
export const recorder = new TelemetryRecorder();

export interface StartTelemetryOptions {
    /** The authenticated post-service client (YsnHttpClient). Instrumented and used for uploads. */
    http: AxiosInstance;
    canUpload: () => boolean;
}

/**
 * Wires telemetry into the app. Call once at module load of the root layout, before any screen
 * mounts, so the first API calls are already traced. Dependencies are passed in so this feature
 * never imports auth, which would make the two features import each other.
 */
export function startTelemetry({ http, canUpload }: StartTelemetryOptions): void {
    if (recorder.isStarted) {
        return;
    }
    instrumentHttpClient(http, recorder);
    installCrashReporting(recorder);
    const uploadUrl = telemetryUploadUrl();
    void recorder.start({
        upload: (batch) => http.post(uploadUrl, batch),
        canUpload,
        client: clientInfo(),
        storage: AsyncStorage,
    });
}

/** Records a named tap on the current screen. target is a story id or option key - never a user id. */
export function trackAction(action: TelemetryAction, target?: string | number): void {
    recorder.trackAction(action, target);
}

export function reportError(error: unknown, fatal = false): void {
    recorder.reportError(error, "render", fatal);
}
