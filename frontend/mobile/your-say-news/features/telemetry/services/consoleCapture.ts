import type { TelemetryLog, TelemetryLogLevel } from "../types";

/** Where captured lines go: the TelemetryRecorder in the app, a fake in tests. */
export interface LogSink {
    log(entry: TelemetryLog): void;
}

type ConsoleMethod = (...args: unknown[]) => void;
type CapturedLevel = "warn" | "error";

/** Enough for a bad session's story; a warning in a render loop cannot push the journey out of the queue. */
export const MAX_CONSOLE_RECORDS_PER_LAUNCH = 100;

const CAPTURED_LEVELS: readonly CapturedLevel[] = ["warn", "error"];

let originals: Partial<Record<TelemetryLogLevel, ConsoleMethod>> = {};
let forwardedThisLaunch = 0;
let recording = false;

/**
 * Forwards console.warn and console.error to telemetry as "console" log records, and still prints
 * them. console.log and console.info stay on the device: they are high-volume debug output from our
 * code and libraries, and the failures they describe already arrive as api_call events.
 *
 * Only strings, numbers, booleans and Error name/message are kept. Objects and arrays become
 * "[object]", because a logged object could be a user profile or a Firebase error with the email in
 * its customData. post-service scrubs emails, opaque ids and long numbers from what is left.
 */
export function installConsoleCapture(sink: LogSink): void {
    uninstallConsoleCapture();
    forwardedThisLaunch = 0;
    for (const level of CAPTURED_LEVELS) {
        const original = console[level] as ConsoleMethod;
        originals[level] = original;
        console[level] = (...args: unknown[]) => {
            original(...args);
            forward(sink, level, args);
        };
    }
}

export function uninstallConsoleCapture(): void {
    for (const level of CAPTURED_LEVELS) {
        const original = originals[level];
        if (original) {
            console[level] = original;
        }
    }
    originals = {};
}

/**
 * Prints a structured diagnostic to the device log (logcat tag ReactNativeJS) through the unpatched
 * console, so a record sent with logEvent is not forwarded a second time as a console line.
 */
export function writeToDeviceLog(level: TelemetryLogLevel, line: string): void {
    const print = originals[level] ?? (console[level] as ConsoleMethod);
    print(line);
}

function forward(sink: LogSink, level: CapturedLevel, args: unknown[]): void {
    if (recording || forwardedThisLaunch >= MAX_CONSOLE_RECORDS_PER_LAUNCH) {
        return;
    }
    recording = true;
    forwardedThisLaunch++;
    try {
        sink.log({ level, name: "console", message: args.map(describeArgument).join(" ") });
    } catch {
        // Telemetry must never break the console it is listening to.
    } finally {
        recording = false;
    }
}

function describeArgument(value: unknown): string {
    if (value instanceof Error) {
        return `${value.name}: ${value.message}`;
    }
    if (typeof value === "string") {
        return value;
    }
    if (typeof value === "number" || typeof value === "boolean" || value === null || value === undefined) {
        return String(value);
    }
    return "[object]";
}
