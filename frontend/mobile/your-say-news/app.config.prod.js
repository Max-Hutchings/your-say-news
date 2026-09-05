// app.config.prod.js
import { readFileSync } from "node:fs";

const selected = process.env.APP_ENV === "development" || process.env.APP_ENV === "prod";
const apiBaseUrl = selected
    ? requiredEnv("EXPO_PUBLIC_API_BASE_URL").replace(/\/$/, "")
    : "https://invalid.local";
const googleServicesFile = selected ? requiredEnv("GOOGLE_SERVICES_JSON") : "google-services.json";
const firebase = selected ? readFirebaseConfig(googleServicesFile) : {};

export default {
    android: {
        googleServicesFile,
    },
    extra: {
        AUTH_MODE: "google",
        FIREBASE_PROJECT_ID: firebase.projectId,
        FIREBASE_API_KEY: firebase.apiKey,
        FIREBASE_APP_ID: firebase.appId,
        GOOGLE_WEB_CLIENT_ID: firebase.webClientId,
        USER_SERVICE_HOST: apiBaseUrl,
        USER_SERVICE_PORT: "",
        POST_SERVICE_HOST: apiBaseUrl,
        POST_SERVICE_PORT: "",
        CHARACTERISTIC_SERVICE_HOST: apiBaseUrl,
        CHARACTERISTIC_SERVICE_PORT: ""
    }
};

function requiredEnv(name) {
    const value = process.env[name];
    if (!value) {
        throw new Error(`Missing required Expo environment variable: ${name}`);
    }
    return value;
}

function readFirebaseConfig(file) {
    const googleServices = JSON.parse(readFileSync(file, "utf8"));
    const projectId = googleServices.project_info?.project_id;
    const client = googleServices.client?.find(
        (candidate) =>
            candidate.client_info?.android_client_info?.package_name === "com.yoursaynews.app",
    );
    const apiKey = client?.api_key?.[0]?.current_key;
    const appId = client?.client_info?.mobilesdk_app_id;
    const webClientId = client?.oauth_client?.find(
        (oauthClient) => oauthClient.client_type === 3,
    )?.client_id;

    if (projectId !== "your-say-news-development" || !apiKey || !appId || !webClientId) {
        throw new Error(
            "GOOGLE_SERVICES_JSON must contain the development Firebase config for com.yoursaynews.app",
        );
    }

    return { projectId, apiKey, appId, webClientId };
}
