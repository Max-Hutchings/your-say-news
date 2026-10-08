import AsyncStorage from "@react-native-async-storage/async-storage";
import type { AxiosInstance } from "axios";
import { clientInfo, telemetryUploadUrl } from "./clientInfo";
import { installConsoleCapture, writeToDeviceLog } from "./consoleCapture";
import { installCrashReporting } from "./crashReporting";
import { instrumentHttpClient } from "./httpInstrumentation";
import { TelemetryRecorder } from "./TelemetryRecorder";
import type { TelemetryLogLevel } from "../types";
import type { TelemetryAction, TelemetryLogAttributes, TelemetryLogName } from "../vocabulary";

/** One recorder per app launch; its session id ties the journey together. */
export const recorder = new TelemetryRecorder();

export interface StartTelemetryOptions {
    /**
     * The post-service client (YsnHttpClient). Instrumented and used for uploads; it adds the Firebase
     * token when someone is signed in and sends without one before that.
     */
    http: AxiosInstance;
}

/**
 * Wires telemetry into the app. Call once at module load of the root layout, before any screen
 * mounts, so the first API calls are already traced. Dependencies are passed in so this feature
 * never imports auth, which would make the two features import each other.
 */
export function startTelemetry({ http }: StartTelemetryOptions): void {
    if (recorder.isStarted) {
        return;
    }
    instrumentHttpClient(http, recorder);
    installCrashReporting(recorder);
    installConsoleCapture(recorder);
    const uploadUrl = telemetryUploadUrl();
    void recorder.start({
        upload: (batch) => http.post(uploadUrl, batch),
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

/**
 * Records a structured diagnostic (Loki: app_log_name="<name>") and prints the same line to the device
 * log. Attributes are bounded codes only - never an email, name or raw error message.
 */
export function logEvent(level: TelemetryLogLevel, name: Exclude<TelemetryLogName, "console">,
                         attributes: TelemetryLogAttributes): void {
    recorder.log({ level, name, attributes });
    const codes = Object.entries(attributes).map(([key, value]) => `${key}=${value}`);
    writeToDeviceLog(level, [name, ...codes].join(" "));
}
