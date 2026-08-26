import type { FirebaseOptions } from "firebase/app";

export const FIREBASE_AUTH_APP_NAME = "your-say-news-auth";

const DEFAULT_PROJECT_ID = "demo-your-say-news";
const DEFAULT_API_KEY = "local-firebase-emulator-key";
const DEFAULT_APP_ID = "1:123456789:web:local-your-say-news";
const DEFAULT_EMULATOR_URL = "http://localhost:9099";

export function firebaseClientConfig(extra: Record<string, unknown>): {
    options: FirebaseOptions;
    emulatorUrl: string;
} {
    const projectId = configuredString(extra.FIREBASE_PROJECT_ID, DEFAULT_PROJECT_ID);
    return {
        options: {
            apiKey: configuredString(extra.FIREBASE_API_KEY, DEFAULT_API_KEY),
            authDomain: `${projectId}.firebaseapp.com`,
            projectId,
            appId: configuredString(extra.FIREBASE_APP_ID, DEFAULT_APP_ID),
        },
        emulatorUrl: configuredString(extra.FIREBASE_AUTH_EMULATOR_URL, DEFAULT_EMULATOR_URL),
    };
}

function configuredString(value: unknown, fallback: string): string {
    return typeof value === "string" && value.trim() ? value.trim() : fallback;
}
