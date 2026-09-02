import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";
import { getApps, initializeApp } from "firebase/app";
import { connectAuthEmulator, initializeAuth } from "firebase/auth";
// Metro resolves Firebase's React Native entry, whose export is missing from the browser typings.
// @ts-expect-error React Native-only Firebase export.
import { getReactNativePersistence } from "firebase/auth";
import { FIREBASE_AUTH_APP_NAME, firebaseClientConfig } from "./firebaseClientConfig";

const extra = Constants.expoConfig?.extra ?? {};
const config = firebaseClientConfig(extra);
const app = getApps().find(({ name }) => name === FIREBASE_AUTH_APP_NAME)
    ?? initializeApp(config.options, FIREBASE_AUTH_APP_NAME);
const emulatorUrl = config.emulatorUrl
    .replace("localhost", "10.0.2.2")
    .replace("127.0.0.1", "10.0.2.2");

export const firebaseAuth = initializeAuth(app, {
    persistence: getReactNativePersistence(AsyncStorage),
});
connectAuthEmulator(firebaseAuth, emulatorUrl, { disableWarnings: true });
