import Constants from "expo-constants";
import { getApp, getApps, initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";

const extra = Constants.expoConfig?.extra ?? {};
const projectId: string = extra.FIREBASE_PROJECT_ID ?? "demo-your-say-news";
const app = getApps().length > 0 ? getApp() : initializeApp({
    apiKey: extra.FIREBASE_API_KEY,
    authDomain: `${projectId}.firebaseapp.com`,
    projectId,
    appId: extra.FIREBASE_APP_ID,
});

export const firebaseAuth = getAuth(app);
connectAuthEmulator(
    firebaseAuth,
    extra.FIREBASE_AUTH_EMULATOR_URL ?? "http://localhost:9099",
    { disableWarnings: true },
);
