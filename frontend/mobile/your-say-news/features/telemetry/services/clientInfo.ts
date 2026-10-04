import Constants from "expo-constants";
import { Platform } from "react-native";
import type { TelemetryClientInfo } from "../types";

/** Coarse device facts only: no model, locale or device identifier. */
export function clientInfo(): TelemetryClientInfo {
    return {
        platform: Platform.OS,
        osVersion: String(Platform.Version ?? "unknown"),
        appVersion: Constants.expoConfig?.version ?? "unknown",
    };
}

/** Same host config the other features use for post-service. */
export function telemetryUploadUrl(): string {
    const extra = Constants.expoConfig?.extra ?? {};
    return `${extra.POST_SERVICE_HOST ?? ""}${extra.POST_SERVICE_PORT ?? ""}/telemetry/mobile`;
}
