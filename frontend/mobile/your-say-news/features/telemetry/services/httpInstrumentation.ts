import axios, { type AxiosAdapter, type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import { traceparent } from "./ids";
import { pathOf, templatePath } from "./routeTemplate";
import type { ApiResult, TelemetryRecorder } from "./TelemetryRecorder";

export const TELEMETRY_UPLOAD_PATH = "/telemetry/mobile";

/**
 * Times every request the instance makes and stamps it with a traceparent header.
 *
 * Wraps the adapter rather than adding interceptors: the adapter runs once per real network
 * attempt, after the auth interceptor has set the token, so the 401-then-refresh retry in
 * YsnHttpClient shows up as two calls instead of hiding the first one.
 */
export function instrumentHttpClient(instance: AxiosInstance, recorder: TelemetryRecorder): void {
    const send = axios.getAdapter(instance.defaults.adapter);
    const instrumented: AxiosAdapter = async (config) => {
        if (isTelemetryUpload(config)) {
            return send(config);
        }
        const span = recorder.startApiCall();
        config.headers.set("traceparent", traceparent(span.traceId, span.spanId));
        const method = config.method ?? "get";
        const path = templatePath(config.url);
        try {
            const response = await send(config);
            recorder.finishApiCall(span, { method, path, status: response.status });
            return response;
        } catch (error) {
            recorder.finishApiCall(span, { method, path, ...failureOf(error) });
            throw error;
        }
    };
    instance.defaults.adapter = instrumented;
}

function isTelemetryUpload(config: InternalAxiosRequestConfig): boolean {
    return pathOf(config.url).endsWith(TELEMETRY_UPLOAD_PATH);
}

function failureOf(error: unknown): Pick<ApiResult, "status" | "errorKind"> {
    if (axios.isCancel(error)) {
        return { errorKind: "cancelled" };
    }
    if (axios.isAxiosError(error)) {
        if (error.response) {
            return { status: error.response.status };
        }
        if (error.code === "ECONNABORTED" || error.code === "ETIMEDOUT") {
            return { errorKind: "timeout" };
        }
    }
    return { errorKind: "network" };
}
