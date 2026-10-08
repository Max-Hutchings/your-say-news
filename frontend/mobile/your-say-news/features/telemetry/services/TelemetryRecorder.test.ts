import { PENDING_STORAGE_KEY, TelemetryRecorder, type KeyValueStorage, type TelemetryOptions } from "./TelemetryRecorder";
import type { TelemetryBatch } from "../types";

const CLIENT = { platform: "ios", osVersion: "18.2", appVersion: "1.4.0" };
const HEX_32 = /^[0-9a-f]{32}$/;
const HEX_16 = /^[0-9a-f]{16}$/;

function memoryStorage(initial: Record<string, string> = {}): KeyValueStorage & { data: Record<string, string> } {
    const data = { ...initial };
    return {
        data,
        getItem: async (key) => data[key] ?? null,
        setItem: async (key, value) => {
            data[key] = value;
        },
        removeItem: async (key) => {
            delete data[key];
        },
    };
}

describe("TelemetryRecorder", () => {
    let clock: number;
    let uploads: TelemetryBatch[];
    let recorder: TelemetryRecorder;

    async function start(overrides: Partial<TelemetryOptions> = {}) {
        await recorder.start({
            upload: async (batch) => {
                uploads.push(batch);
            },
            client: CLIENT,
            now: () => clock,
            flushIntervalMs: 60_000,
            ...overrides,
        });
    }

    beforeEach(() => {
        clock = 1_000;
        uploads = [];
        recorder = new TelemetryRecorder();
    });

    afterEach(() => recorder.stop());

    it("records a journey where taps and API calls are child spans of the screen they happened on", async () => {
        await start();
        recorder.enterScreen("/posts/[postId]", "42");
        clock = 1_500;
        recorder.trackAction("vote.cast", 42);
        const call = recorder.startApiCall();
        clock = 1_684;
        recorder.finishApiCall(call, { method: "post", path: "/votes", status: 201 });
        clock = 13_000;
        recorder.enterScreen("/posts/[postId]/results", "42");
        await recorder.flush();

        const [appStart, enter, action, api, exit, nextEnter] = uploads[0].events;
        expect(uploads[0].sessionId).toMatch(HEX_32);
        expect(uploads[0].client).toEqual(CLIENT);
        expect(appStart).toMatchObject({ type: "app_start", timestampMs: 1_000, screen: "/" });
        expect(enter).toMatchObject({ type: "screen_enter", screen: "/posts/[postId]", target: "42" });
        expect(enter.traceId).toMatch(HEX_32);
        expect(enter.spanId).toMatch(HEX_16);
        expect(action).toMatchObject({
            type: "action", action: "vote.cast", target: "42", screen: "/posts/[postId]",
            traceId: enter.traceId, parentSpanId: enter.spanId, timestampMs: 1_500,
        });
        expect(api).toMatchObject({
            type: "api_call", method: "POST", path: "/votes", status: 201,
            timestampMs: 1_500, endTimestampMs: 1_684,
            traceId: enter.traceId, parentSpanId: enter.spanId,
        });
        expect(exit).toMatchObject({
            type: "screen_exit", screen: "/posts/[postId]", timestampMs: 1_000, endTimestampMs: 13_000,
            traceId: enter.traceId, spanId: enter.spanId,
        });
        expect(nextEnter.traceId).not.toBe(enter.traceId);
        expect(new Set([enter.spanId, action.spanId, api.spanId]).size).toBe(3);
    });

    it("ignores a re-render of the same screen but not a different story on the same route", async () => {
        await start();
        recorder.enterScreen("/posts/[postId]", "1");
        recorder.enterScreen("/posts/[postId]", "1");
        recorder.enterScreen("/posts/[postId]", "2");
        await recorder.flush();

        expect(uploads[0].events.map((e) => `${e.type}:${e.target ?? ""}`)).toEqual([
            "app_start:", "screen_enter:1", "screen_exit:1", "screen_enter:2",
        ]);
    });

    it("closes the screen on background and reopens it with a fresh trace on return", async () => {
        await start();
        recorder.enterScreen("/", undefined);
        clock = 5_000;
        recorder.appBackgrounded();
        clock = 60_000;
        recorder.appForegrounded();
        await recorder.flush();

        const events = uploads.flatMap((batch) => batch.events);
        expect(events.map((e) => e.type)).toEqual([
            "app_start", "screen_enter", "app_state", "screen_exit", "app_state", "screen_enter",
        ]);
        expect(events[2].appState).toBe("background");
        expect(events[3].endTimestampMs).toBe(5_000);
        expect(events[4].appState).toBe("active");
        expect(events[5]).toMatchObject({ screen: "/", timestampMs: 60_000 });
        expect(events[5].traceId).not.toBe(events[1].traceId);
    });

    it("uploads before anyone signs in and links a sign-in failure to the screen it happened on", async () => {
        await start();
        recorder.enterScreen("/sign-in", undefined);
        clock = 2_000;
        recorder.log({ level: "warn", name: "auth.sign_in_failed", attributes: { stage: "google", code: "10" } });
        await recorder.flush();

        const [, enter, log] = uploads[0].events;
        expect(log).toEqual({
            type: "log", timestampMs: 2_000, screen: "/sign-in", traceId: enter.traceId, spanId: enter.spanId,
            log: { level: "warn", name: "auth.sign_in_failed", attributes: { stage: "google", code: "10" } },
        });
    });

    it("sends a structured diagnostic at once but leaves console lines for the next timed flush", async () => {
        await start();
        recorder.log({ level: "error", name: "console", message: "Render warning" });
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(uploads).toHaveLength(0);

        recorder.log({ level: "warn", name: "auth.sign_in_failed", attributes: { stage: "firebase", code: "auth/x" } });
        await new Promise((resolve) => setTimeout(resolve, 0));
        expect(uploads.flatMap((b) => b.events).map((e) => e.log?.name ?? e.type))
            .toEqual(["app_start", "console", "auth.sign_in_failed"]);
    });

    it("cuts a console line to the server's message limit", async () => {
        await start();
        recorder.log({ level: "warn", name: "console", message: "z".repeat(5_000) });
        await recorder.flush();

        expect(uploads[0].events[1].log?.message).toHaveLength(300);
    });

    it("re-queues a failed upload in its original order and sends it next time", async () => {
        let fail = true;
        await start({
            upload: async (batch) => {
                if (fail) throw new Error("offline");
                uploads.push(batch);
            },
        });
        recorder.trackAction("feed.refresh");
        await recorder.flush();
        recorder.trackAction("feed.load_more");
        fail = false;
        await recorder.flush();

        expect(uploads[0].events.map((e) => e.action ?? e.type)).toEqual(["app_start", "feed.refresh", "feed.load_more"]);
    });

    it("drops a batch the server rejects as invalid instead of retrying it forever", async () => {
        let attempts = 0;
        await start({
            upload: async () => {
                attempts++;
                throw { response: { status: 400 } };
            },
        });
        await recorder.flush();
        await recorder.flush();

        expect(attempts).toBe(1);
    });

    it("sends at most 100 events per request", async () => {
        await start();
        for (let i = 0; i < 149; i++) recorder.trackAction("feed.load_more");
        await recorder.flush();

        expect(uploads.map((batch) => batch.events.length)).toEqual([100, 50]);
    });

    it("keeps only the newest 500 events while uploads are impossible", async () => {
        let online = false;
        await start({
            upload: async (batch) => {
                if (!online) throw new Error("offline");
                uploads.push(batch);
            },
        });
        for (let i = 0; i < 600; i++) recorder.trackAction("post.share", i);
        await recorder.flush();
        online = true;
        await recorder.flush();

        const events = uploads.flatMap((batch) => batch.events);
        expect(events).toHaveLength(500);
        expect(events[0].target).toBe("100");
        expect(events[499].target).toBe("599");
    });

    it("reports a crash once even when the boundary and the global handler both see it", async () => {
        await start();
        recorder.enterScreen("/posts/[postId]", "42");
        const crash = new TypeError("x is undefined");
        recorder.reportError(crash, "render", false);
        recorder.reportError(crash, "global", true);
        await recorder.flush();

        const errors = uploads.flatMap((b) => b.events).filter((e) => e.type === "error");
        expect(errors).toHaveLength(1);
        expect(errors[0]).toMatchObject({
            errorName: "TypeError", errorMessage: "x is undefined", errorKind: "render",
            fatal: false, screen: "/posts/[postId]",
        });
    });

    it("writes a fatal crash to storage at once, even while an upload is still in flight", async () => {
        const storage = memoryStorage();
        await start({ upload: () => new Promise(() => undefined), storage });
        void recorder.flush();
        recorder.enterScreen("/posts/[postId]", "42");
        recorder.reportError(new TypeError("boom"), "global", true);
        await Promise.resolve();
        await Promise.resolve();

        const saved: TelemetryBatch[] = JSON.parse(storage.data[PENDING_STORAGE_KEY]);
        expect(saved[0].events.map((e) => e.type)).toEqual(["app_start", "screen_enter", "error"]);
        expect(saved[0].events[2]).toMatchObject({ errorName: "TypeError", fatal: true });
    });

    it("cuts crash text to the server's field limits so one crash cannot get a batch rejected", async () => {
        await start();
        const crash = new Error("y".repeat(5_000));
        crash.name = "N".repeat(200);
        recorder.reportError(crash, "global", false);
        await recorder.flush();

        const error = uploads[0].events.find((e) => e.type === "error")!;
        expect(error.errorMessage).toHaveLength(300);
        expect(error.errorName).toHaveLength(64);
    });

    it("saves unsent events and uploads them on the next launch under the old session id", async () => {
        const storage = memoryStorage();
        await start({ upload: async () => { throw new Error("offline"); }, storage });
        recorder.enterScreen("/posts/[postId]", "42");
        recorder.reportError(new Error("boom"), "global", true);
        await recorder.flush();
        const crashedSession = recorder.sessionId;
        expect(JSON.parse(storage.data[PENDING_STORAGE_KEY])[0].sessionId).toBe(crashedSession);
        recorder.stop();

        recorder = new TelemetryRecorder();
        await start({ storage });
        await recorder.flush();

        expect(uploads.map((batch) => batch.sessionId)).toEqual([crashedSession, recorder.sessionId]);
        expect(uploads[0].events.map((e) => e.type)).toEqual(["app_start", "screen_enter", "error"]);
        expect(storage.data[PENDING_STORAGE_KEY]).toBeUndefined();
    });
});
