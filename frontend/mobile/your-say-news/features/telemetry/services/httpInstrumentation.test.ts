import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from "axios";
import { instrumentHttpClient } from "./httpInstrumentation";
import { TelemetryRecorder } from "./TelemetryRecorder";
import type { TelemetryBatch } from "../types";

/** A real axios instance whose network layer is replaced, so interceptors and validateStatus still run. */
function clientAnswering(answer: (config: InternalAxiosRequestConfig) => ReturnType<AxiosAdapter>) {
    const seen: InternalAxiosRequestConfig[] = [];
    const instance = axios.create({
        adapter: (config) => {
            seen.push(config);
            return answer(config);
        },
    });
    return { instance, seen };
}

function respond(status: number) {
    return (config: InternalAxiosRequestConfig) => {
        const response = { status, statusText: "", headers: {}, config, data: {} };
        if (status >= 400) {
            return Promise.reject(new AxiosError("failed", undefined, config, null, response));
        }
        return Promise.resolve(response);
    };
}

describe("instrumentHttpClient", () => {
    let recorder: TelemetryRecorder;
    let uploads: TelemetryBatch[];

    beforeEach(async () => {
        recorder = new TelemetryRecorder();
        uploads = [];
        await recorder.start({
            upload: async (batch) => {
                uploads.push(batch);
            },
            canUpload: () => true,
            client: { platform: "android", osVersion: "15", appVersion: "1.0.0" },
            flushIntervalMs: 60_000,
        });
        recorder.enterScreen("/posts/[postId]", "42");
    });

    afterEach(() => recorder.stop());

    async function apiCalls() {
        await recorder.flush();
        return uploads.flatMap((batch) => batch.events).filter((event) => event.type === "api_call");
    }

    it("sends a traceparent that joins the current screen's trace and records the call", async () => {
        const { instance, seen } = clientAnswering(respond(200));
        instrumentHttpClient(instance, recorder);

        await instance.get("https://dev.yoursaynews.com/api/posts/42?include=sources");

        const [call] = await apiCalls();
        const screen = recorder.currentScreen()!;
        expect(seen[0].headers.get("traceparent")).toBe(`00-${screen.traceId}-${call.spanId}-01`);
        expect(call).toMatchObject({
            method: "GET", path: "/api/posts/{id}", status: 200,
            traceId: screen.traceId, parentSpanId: screen.spanId, screen: "/posts/[postId]",
        });
        expect(call.errorKind).toBeUndefined();
    });

    it("never puts a member id or email from the URL into the event", async () => {
        const { instance } = clientAnswering(respond(200));
        instrumentHttpClient(instance, recorder);

        await instance.delete("http://localhost:8082/social/follows/17");
        await instance.get("http://localhost:8082/your-say-user/email/riley.reader%40example.com");
        await instance.get("http://localhost:8082/social/Xk3vT9qLmZ2wR8pYc4nB7sD1fGh0/followers");

        expect((await apiCalls()).map((call) => call.path)).toEqual([
            "/social/follows/{id}",
            "/your-say-user/email/{id}",
            "/social/{id}/followers",
        ]);
    });

    it("records an HTTP error status and still rejects to the caller", async () => {
        const { instance } = clientAnswering(respond(409));
        instrumentHttpClient(instance, recorder);

        await expect(instance.post("/votes", { postId: 42, optionId: 1 })).rejects.toBeInstanceOf(AxiosError);

        const [call] = await apiCalls();
        expect(call).toMatchObject({ method: "POST", path: "/votes", status: 409 });
    });

    it.each([
        ["timeout", new AxiosError("timeout", "ECONNABORTED")],
        ["network", new AxiosError("Network Error", "ERR_NETWORK")],
        ["cancelled", new axios.CanceledError("cancelled")],
    ])("classifies a request with no response as %s", async (kind, error) => {
        const { instance } = clientAnswering(() => Promise.reject(error));
        instrumentHttpClient(instance, recorder);

        await expect(instance.get("/feed")).rejects.toBe(error);

        const [call] = await apiCalls();
        expect(call).toMatchObject({ path: "/feed", errorKind: kind });
        expect(call.status).toBeUndefined();
    });

    it("records each attempt of a retried request separately", async () => {
        let attempt = 0;
        const { instance } = clientAnswering((config) => respond(attempt++ === 0 ? 401 : 200)(config));
        instance.interceptors.response.use(undefined, (error) => instance(error.config));
        instrumentHttpClient(instance, recorder);

        await instance.get("/your-say-user");

        expect((await apiCalls()).map((call) => call.status)).toEqual([401, 200]);
    });

    it("does not trace its own telemetry uploads", async () => {
        const { instance, seen } = clientAnswering(respond(202));
        instrumentHttpClient(instance, recorder);

        await instance.post("http://localhost:8082/telemetry/mobile", {});

        expect(seen[0].headers.get("traceparent")).toBeUndefined();
        expect(await apiCalls()).toHaveLength(0);
    });
});
