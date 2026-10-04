import type { LoginResult } from "./types";

/**
 * The sign-in screen's error text. Hosted builds include the stage and provider code so a tester's
 * screenshot identifies the failure without device logs.
 */
export function signInErrorMessage(result: LoginResult, hostedGoogleAuth: boolean): string | null {
    if (result.status === "signed-in") {
        return null;
    }
    if (!hostedGoogleAuth) {
        return "That test account could not be signed in.";
    }
    if (result.status === "cancelled") {
        return "Google sign-in was cancelled.";
    }
    const detail = `(${result.stage}: ${result.code})`;
    switch (result.stage) {
        case "google":
            return `Google rejected this sign-in ${detail}.`;
        case "firebase":
            return `Firebase rejected this Google account ${detail}.`;
        case "server":
            return `Your Say News could not load your account ${detail}.`;
    }
}
