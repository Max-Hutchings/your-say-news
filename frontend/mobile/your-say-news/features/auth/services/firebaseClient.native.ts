import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";
import { getApp, getApps, initializeApp } from "firebase/app";
import { connectAuthEmulator, initializeAuth } from "firebase/auth";
// Metro resolves Firebase's React Native entry, whose export is missing from the browser typings.
// @ts-expect-error React Native-only Firebase export.
import { getReactNativePersistence } from "firebase/auth";

const extra = Constants.expoConfig?.extra ?? {};
const projectId: string = extra.FIREBASE_PROJECT_ID ?? "demo-your-say-news";
const app = getApps().length > 0 ? getApp() : initializeApp({
    apiKey: extra.FIREBASE_API_KEY,
    authDomain: `${projectId}.firebaseapp.com`,
    projectId,
    appId: extra.FIREBASE_APP_ID,
});
const configuredUrl: string = extra.FIREBASE_AUTH_EMULATOR_URL ?? "http://localhost:9099";
const emulatorUrl = configuredUrl
    .replace("localhost", "10.0.2.2")
    .replace("127.0.0.1", "10.0.2.2");

export const firebaseAuth = initializeAuth(app, {
    persistence: getReactNativePersistence(AsyncStorage),
});
connectAuthEmulator(firebaseAuth, emulatorUrl, { disableWarnings: true });
