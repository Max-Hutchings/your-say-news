/**
 * Telemetry feature - public face.
 *
 * Screen views, API calls, app lifecycle and crashes are recorded automatically once the root
 * layout calls startTelemetry and useScreenTracking. Features only call trackAction for taps that
 * matter to a click journey.
 */
export { startTelemetry, trackAction, reportError } from "./services/telemetry";
export { useScreenTracking } from "./hooks/use-screen-tracking";
export { ScreenErrorBoundary } from "./components/ScreenErrorBoundary";
export type { TelemetryAction } from "./vocabulary";
