/**
 * W3C trace-context ids. They only need to be unique, not secret, so Math.random is enough and
 * avoids a native crypto dependency.
 */
export function randomHex(bytes: number): string {
    let hex = "";
    for (let i = 0; i < bytes; i++) {
        hex += Math.floor(Math.random() * 256).toString(16).padStart(2, "0");
    }
    return /^0+$/.test(hex) ? randomHex(bytes) : hex;
}

export const newTraceId = () => randomHex(16);
export const newSpanId = () => randomHex(8);
export const newSessionId = () => randomHex(16);

/** The header post-service's OpenTelemetry instrumentation reads to join the app's trace. */
export function traceparent(traceId: string, spanId: string): string {
    return `00-${traceId}-${spanId}-01`;
}
