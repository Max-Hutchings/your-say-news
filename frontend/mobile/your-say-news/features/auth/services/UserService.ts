
import YsnHttpClient from "./requests";
import Constants from "expo-constants";
import {OnboardingStatus, User} from "../types";

function userServiceBase(): string {
    const host: string = Constants.expoConfig?.extra?.USER_SERVICE_HOST;
    const port: string = Constants.expoConfig?.extra?.USER_SERVICE_PORT;
    return host + port;
}

export async function getUser(): Promise<User | null> {
    try {
        const response = await YsnHttpClient.getSecure().get(userServiceBase() + "/your-say-user");
        if (response.status === 200) {
            return response.data; // already typed as User
        }

        console.error("Failed to authenticate user:", response.statusText);
        return null;

    } catch (err: any) {
        console.error("Network/request error:", err.message);
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
        console.error("Failed to fetch onboarding status:", response.statusText);
        return null;
    } catch (err: any) {
        console.error("Network/request error:", err.message);
        return null;
    }
}
