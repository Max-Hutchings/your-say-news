import type { TelemetryLogAttributes, TelemetryLogName } from "./vocabulary";

/**
 * Wire format of POST /telemetry/mobile (MobileTelemetryBatchDto in post-service).
 *
 * Never put a user id, email, vote choice or characteristic answer in any field: the server logs
 * these events next to a session id.
 */
export type TelemetryEventType =
    | "app_start"
    | "app_state"
    | "screen_enter"
    | "screen_exit"
    | "action"
    | "api_call"
    | "error"
    | "log";

export type TelemetryLogLevel = "info" | "warn" | "error";

/**
 * A diagnostic log record. attributes are bounded codes only (TelemetryLogAttributes); message is
 * console text, which the server scrubs of emails and ids again before it reaches Loki.
 */
export interface TelemetryLog {
    level: TelemetryLogLevel;
    name: TelemetryLogName;
    message?: string;
    attributes?: TelemetryLogAttributes;
}

export interface TelemetryEvent {
    type: TelemetryEventType;
    timestampMs: number;
    endTimestampMs?: number;
    screen: string;
    target?: string;
    traceId?: string;
    spanId?: string;
    parentSpanId?: string;
    action?: string;
    method?: string;
    path?: string;
    status?: number;
    /** api_call: network | timeout | cancelled. error: render | global. */
    errorKind?: string;
    errorName?: string;
    errorMessage?: string;
    fatal?: boolean;
    appState?: "active" | "background";
    log?: TelemetryLog;
}

export interface TelemetryClientInfo {
    platform: string;
    osVersion: string;
    appVersion: string;
}

export interface TelemetryBatch {
    sessionId: string;
    client: TelemetryClientInfo;
    events: TelemetryEvent[];
}

/** One open screen. Every action and API call made on it joins this trace. */
export interface ScreenContext {
    screen: string;
    target?: string;
    traceId: string;
    spanId: string;
    startedAtMs: number;
}
