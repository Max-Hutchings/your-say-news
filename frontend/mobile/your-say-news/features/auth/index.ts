/**
 * Auth feature — public face.
 *
 * Routes and other features import auth ONLY from here, never from the
 * internal services/ or hooks/ folders.
 */

export { useAuthStore } from "./services/authContext";
export { default as YsnHttpClient } from "./services/requests";
export { useFetchWithAuth } from "./hooks/use-fetch-with-auth";
export type { User, UserState } from "./types";
