import type { TelemetryAction } from "../vocabulary";
import type {
    ScreenContext,
    TelemetryBatch,
    TelemetryClientInfo,
    TelemetryEvent,
    TelemetryLog,
} from "../types";
import { newSessionId, newSpanId, newTraceId } from "./ids";

/** Upload one batch. Rejects with the HTTP error so the recorder can tell "retry" from "give up". */
export type TelemetryUpload = (batch: TelemetryBatch) => Promise<unknown>;

export interface KeyValueStorage {
    getItem(key: string): Promise<string | null>;
    setItem(key: string, value: string): Promise<void>;
    removeItem(key: string): Promise<void>;
}

export interface TelemetryOptions {
    /**
     * Sends with the user's Firebase token when there is one and without it otherwise: post-service
     * accepts batches from people who have not signed in, so a failed sign-in still reaches Grafana.
     */
    upload: TelemetryUpload;
    client: TelemetryClientInfo;
    storage?: KeyValueStorage;
    now?: () => number;
    flushIntervalMs?: number;
}

/** A started API call, finished by finishApiCall once the response (or failure) arrives. */
export interface ApiSpan {
    traceId: string;
    spanId: string;
    parentSpanId?: string;
    screen: string;
    startedAtMs: number;
}

export interface ApiResult {
    method: string;
    path: string;
    status?: number;
    errorKind?: "network" | "timeout" | "cancelled";
}

export const PENDING_STORAGE_KEY = "ysn-telemetry-pending";
const MAX_BATCH_EVENTS = 100;
const MAX_PENDING_EVENTS = 500;
const FLUSH_AT_EVENTS = 50;
const DEFAULT_FLUSH_INTERVAL_MS = 10_000;
const LAUNCH_SCREEN = "/";
/** Within post-service's field limits, so one long crash message cannot get a whole batch rejected. */
const MAX_ERROR_NAME_LENGTH = 64;
const MAX_ERROR_MESSAGE_LENGTH = 300;

/**
 * Records the click journey of one app launch and uploads it in batches.
 *
 * Trace model: every screen view is one trace. Its span starts on screen_enter and is sent on
 * screen_exit; taps and API calls made on that screen are child spans. API calls also send the ids
 * in a traceparent header, so post-service's own spans join the same trace in Tempo.
 *
 * Unsent events are written to storage when the app backgrounds or crashes, and sent on the next
 * launch under their original session id, so a crash journey is not lost with the process.
 */
export class TelemetryRecorder {
    readonly sessionId = newSessionId();

    private options: TelemetryOptions | null = null;
    private screen: ScreenContext | null = null;
    private screenBeforeBackground: ScreenContext | null = null;
    private pending: TelemetryEvent[] = [];
    private inFlight: TelemetryEvent[] | null = null;
    private backlog: TelemetryBatch[] = [];
    private flushing: Promise<void> | null = null;
    private flushRequestedWhileRunning = false;
    private timer: ReturnType<typeof setInterval> | null = null;
    private storedSomething = false;
    private readonly reportedErrors = new WeakSet<object>();

    get isStarted(): boolean {
        return this.options !== null;
    }

    async start(options: TelemetryOptions): Promise<void> {
        if (this.options) {
            return;
        }
        this.options = options;
        this.push({ type: "app_start", timestampMs: this.now(), screen: LAUNCH_SCREEN });
        this.timer = setInterval(() => void this.flush(), options.flushIntervalMs ?? DEFAULT_FLUSH_INTERVAL_MS);
        await this.restorePreviousLaunches();
    }

    stop(): void {
        if (this.timer) {
            clearInterval(this.timer);
        }
        this.timer = null;
        this.options = null;
    }

    currentScreen(): ScreenContext | null {
        return this.screen;
    }

    enterScreen(screen: string, target?: string): void {
        if (this.screen?.screen === screen && this.screen.target === target) {
            return;
        }
        this.exitScreen();
        const context: ScreenContext = {
            screen,
            target,
            traceId: newTraceId(),
            spanId: newSpanId(),
            startedAtMs: this.now(),
        };
        this.screen = context;
        this.push({
            type: "screen_enter",
            timestampMs: context.startedAtMs,
            screen,
            target,
            traceId: context.traceId,
            spanId: context.spanId,
        });
    }

    exitScreen(): void {
        const context = this.screen;
        if (!context) {
            return;
        }
        this.screen = null;
        this.push({
            type: "screen_exit",
            timestampMs: context.startedAtMs,
            endTimestampMs: this.now(),
            screen: context.screen,
            target: context.target,
            traceId: context.traceId,
            spanId: context.spanId,
        });
    }

    appBackgrounded(): void {
        this.push({ type: "app_state", timestampMs: this.now(), screen: this.screenName(), appState: "background" });
        this.screenBeforeBackground = this.screen;
        this.exitScreen();
        void this.flush();
    }

    /** Coming back starts a fresh view of the same screen, so dwell time excludes time away. */
    appForegrounded(): void {
        const resumed = this.screenBeforeBackground;
        this.screenBeforeBackground = null;
        this.push({
            type: "app_state",
            timestampMs: this.now(),
            screen: resumed?.screen ?? this.screenName(),
            appState: "active",
        });
        if (resumed && !this.screen) {
            this.enterScreen(resumed.screen, resumed.target);
        }
    }

    trackAction(action: TelemetryAction, target?: string | number): void {
        const parent = this.screen;
        this.push({
            type: "action",
            timestampMs: this.now(),
            screen: this.screenName(),
            target: target === undefined ? undefined : String(target),
            action,
            traceId: parent?.traceId ?? newTraceId(),
            spanId: newSpanId(),
            parentSpanId: parent?.spanId,
        });
    }

    startApiCall(): ApiSpan {
        const parent = this.screen;
        return {
            traceId: parent?.traceId ?? newTraceId(),
            spanId: newSpanId(),
            parentSpanId: parent?.spanId,
            screen: this.screenName(),
            startedAtMs: this.now(),
        };
    }

    finishApiCall(span: ApiSpan, result: ApiResult): void {
        this.push({
            type: "api_call",
            timestampMs: span.startedAtMs,
            endTimestampMs: this.now(),
            screen: span.screen,
            traceId: span.traceId,
            spanId: span.spanId,
            parentSpanId: span.parentSpanId,
            method: result.method.toUpperCase(),
            path: result.path,
            status: result.status,
            errorKind: result.errorKind,
        });
    }

    /** Reported once per error object, even when both the error boundary and the global handler see it. */
    reportError(error: unknown, source: "render" | "global", fatal: boolean): void {
        if (typeof error === "object" && error !== null) {
            if (this.reportedErrors.has(error)) {
                return;
            }
            this.reportedErrors.add(error);
        }
        const parent = this.screen;
        this.push({
            type: "error",
            timestampMs: this.now(),
            screen: this.screenName(),
            traceId: parent?.traceId ?? newTraceId(),
            spanId: newSpanId(),
            parentSpanId: parent?.spanId,
            errorKind: source,
            errorName: (error instanceof Error ? error.name : "Error").slice(0, MAX_ERROR_NAME_LENGTH),
            errorMessage: (error instanceof Error ? error.message : String(error)).slice(0, MAX_ERROR_MESSAGE_LENGTH),
            fatal,
        });
        if (fatal) {
            void this.persist();
        }
        void this.flush();
    }

    /**
     * Records a diagnostic log on the current screen's trace. Structured records (a sign-in failure)
     * are sent at once, because the person may close the app straight after; console lines wait for
     * the next timed flush so a chatty screen cannot turn into a stream of requests.
     */
    log(entry: TelemetryLog): void {
        const parent = this.screen;
        this.push({
            type: "log",
            timestampMs: this.now(),
            screen: this.screenName(),
            traceId: parent?.traceId,
            spanId: parent?.spanId,
            log: entry.message === undefined
                ? entry
                : { ...entry, message: entry.message.slice(0, MAX_ERROR_MESSAGE_LENGTH) },
        });
        if (entry.name !== "console") {
            void this.flush();
        }
    }

    /**
     * Sends earlier launches first, then this launch. One upload loop runs at a time; a flush asked
     * for mid-loop (for example right after sign-in) runs again once it ends, unless an upload just
     * failed, so an offline device does not retry in a tight loop.
     */
    flush(): Promise<void> {
        if (this.flushing) {
            this.flushRequestedWhileRunning = true;
            return this.flushing;
        }
        this.flushing = this.uploadUntilSettled().finally(() => {
            this.flushing = null;
        });
        return this.flushing;
    }

    private async uploadUntilSettled(): Promise<void> {
        let uploadFailed: boolean;
        do {
            this.flushRequestedWhileRunning = false;
            uploadFailed = await this.uploadQueued();
        } while (this.flushRequestedWhileRunning && !uploadFailed);
    }

    /** Returns true when an upload failed and the events went back on the queue. */
    private async uploadQueued(): Promise<boolean> {
        const options = this.options;
        if (!options) {
            return false;
        }
        let uploadFailed = false;
        try {
            await this.uploadBacklog(options);
            await this.uploadPending(options);
        } catch {
            this.returnInFlightToQueue();
            uploadFailed = true;
        }
        await this.persist();
        return uploadFailed;
    }

    private async uploadBacklog(options: TelemetryOptions): Promise<void> {
        while (this.backlog.length > 0) {
            await this.uploadOrDiscard(options, this.backlog[0]);
            this.backlog.shift();
        }
    }

    private async uploadPending(options: TelemetryOptions): Promise<void> {
        while (this.pending.length > 0) {
            this.inFlight = this.pending.splice(0, MAX_BATCH_EVENTS);
            await this.uploadOrDiscard(options, {
                sessionId: this.sessionId,
                client: options.client,
                events: this.inFlight,
            });
            this.inFlight = null;
        }
    }

    /** A 400 means this batch can never be accepted; retrying it would block every later batch. */
    private async uploadOrDiscard(options: TelemetryOptions, batch: TelemetryBatch): Promise<void> {
        try {
            await options.upload(batch);
        } catch (error) {
            if (httpStatusOf(error) !== 400) {
                throw error;
            }
        }
    }

    private returnInFlightToQueue(): void {
        if (this.inFlight) {
            this.pending = this.inFlight.concat(this.pending);
            this.inFlight = null;
            this.dropOldestBeyondLimit();
        }
    }

    private push(event: TelemetryEvent): void {
        this.pending.push(event);
        this.dropOldestBeyondLimit();
        if (this.pending.length >= FLUSH_AT_EVENTS) {
            void this.flush();
        }
    }

    private dropOldestBeyondLimit(): void {
        if (this.pending.length > MAX_PENDING_EVENTS) {
            this.pending.splice(0, this.pending.length - MAX_PENDING_EVENTS);
        }
    }

    private async restorePreviousLaunches(): Promise<void> {
        const storage = this.options?.storage;
        if (!storage) {
            return;
        }
        try {
            const stored = await storage.getItem(PENDING_STORAGE_KEY);
            const batches = stored ? (JSON.parse(stored) as TelemetryBatch[]) : [];
            this.backlog.push(...batches.filter((batch) => batch.sessionId !== this.sessionId));
            this.storedSomething = batches.length > 0;
        } catch {
            // Unreadable leftovers are not worth crashing a launch over.
            await storage.removeItem(PENDING_STORAGE_KEY).catch(() => undefined);
        }
    }

    private async persist(): Promise<void> {
        const storage = this.options?.storage;
        if (!storage) {
            return;
        }
        const unsent = [...this.backlog];
        const currentEvents = [...(this.inFlight ?? []), ...this.pending];
        if (currentEvents.length > 0 && this.options) {
            unsent.push({ sessionId: this.sessionId, client: this.options.client, events: currentEvents });
        }
        try {
            if (unsent.length > 0) {
                await storage.setItem(PENDING_STORAGE_KEY, JSON.stringify(unsent));
                this.storedSomething = true;
            } else if (this.storedSomething) {
                await storage.removeItem(PENDING_STORAGE_KEY);
                this.storedSomething = false;
            }
        } catch {
            // Storage full or unavailable: the in-memory queue still uploads on the next flush.
        }
    }

    private screenName(): string {
        return this.screen?.screen ?? LAUNCH_SCREEN;
    }

    private now(): number {
        return this.options?.now?.() ?? Date.now();
    }
}

function httpStatusOf(error: unknown): number | undefined {
    if (typeof error === "object" && error !== null && "response" in error) {
        return (error as { response?: { status?: number } }).response?.status;
    }
    return undefined;
}
