/**
 * Telemetry feature - public face.
 *
 * Screen views, API calls, app lifecycle and crashes are recorded automatically once the root
 * layout calls startTelemetry and useScreenTracking, from before sign-in onwards. console.warn and
 * console.error are forwarded too. Features call trackAction for taps that matter to a click journey
 * and logEvent for structured diagnostics such as a failed sign-in.
 */
export { startTelemetry, trackAction, reportError, logEvent } from "./services/telemetry";
export { useScreenTracking } from "./hooks/use-screen-tracking";
export { ScreenErrorBoundary } from "./components/ScreenErrorBoundary";
export type { TelemetryAction, TelemetryLogAttributes, TelemetryLogName } from "./vocabulary";
