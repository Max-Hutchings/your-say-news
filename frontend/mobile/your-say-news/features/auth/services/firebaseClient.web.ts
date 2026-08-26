import Constants from "expo-constants";
import { getApps, initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";
import { FIREBASE_AUTH_APP_NAME, firebaseClientConfig } from "./firebaseClientConfig";

const extra = Constants.expoConfig?.extra ?? {};
const config = firebaseClientConfig(extra);
const app = getApps().find(({ name }) => name === FIREBASE_AUTH_APP_NAME)
    ?? initializeApp(config.options, FIREBASE_AUTH_APP_NAME);

export const firebaseAuth = getAuth(app);
connectAuthEmulator(
    firebaseAuth,
    config.emulatorUrl,
    { disableWarnings: true },
);
