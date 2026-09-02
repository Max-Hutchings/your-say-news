
import YsnHttpClient from "./requests";
import Constants from "expo-constants";
import {OnboardingStatus, User} from "../types";

function userServiceBase(): string {
    const host: string = Constants.expoConfig?.extra?.USER_SERVICE_HOST;
    const port: string = Constants.expoConfig?.extra?.USER_SERVICE_PORT;
    return host + port;
}

/**
 * Result of checking a session restored from storage against the server.
 *
 * The distinction matters: a rejected token means the stored session is dead and every trace of it
 * must be wiped, while an unreachable server means we simply do not know yet and must not throw a
 * valid session away.
 */
export type SessionCheck =
    | { state: "valid"; user: User }
    | { state: "unauthenticated" }
    | { state: "unreachable" };

/**
 * Ask the server whether the stored credentials still identify someone. Used on startup so a
 * persisted session is either genuinely signed in or cleared — never a stale identity that outlives
 * its token and gets served to whoever opens the app next.
 */
export async function verifySession(): Promise<SessionCheck> {
    try {
        const response = await YsnHttpClient.getSecure().get(userServiceBase() + "/your-say-user");
        if (response.status === 200 && response.data) {
            return { state: "valid", user: response.data };
        }
        return { state: "unauthenticated" };
    } catch (err: any) {
        const status: number | undefined = err?.response?.status;

        // Only the server actually rejecting the caller proves the session is dead.
        if (status === 401 || status === 403) {
            return { state: "unauthenticated" };
        }

        // Anything else — no response at all (network failure, server down, CORS) or a server-side
        // fault — tells us nothing about the session. Destroying credentials over a transient 503
        // would sign people out every time the backend stumbles.
        return { state: "unreachable" };
    }
}

export async function getUser(): Promise<User | null> {
    try {
        const response = await YsnHttpClient.getSecure().get(userServiceBase() + "/your-say-user");
        if (response.status === 200) {
            return response.data; // already typed as User
        }

        console.info("Failed to authenticate user:", response.statusText);
        return null;

    } catch (err: any) {
        console.info("Network/request error:", err.message);
        return null;
    }
}

/**
 * Ask the server how far the user is through onboarding — consent given and a characteristic profile
 * saved. The client routes on this so a returning, fully-onboarded user goes straight to the feed.
 */
export async function getOnboardingStatus(): Promise<OnboardingStatus | null> {
    try {
        const response = await YsnHttpClient.getSecure().get(userServiceBase() + "/your-say-user/onboarding");
        if (response.status === 200) {
            return response.data;
        }
        console.info("Failed to fetch onboarding status:", response.statusText);
        return null;
    } catch (err: any) {
        console.info("Network/request error:", err.message);
        return null;
    }
}
