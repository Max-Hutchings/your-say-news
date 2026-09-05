// app.config.prod.js
const selected = process.env.APP_ENV === "development" || process.env.APP_ENV === "prod";
const apiBaseUrl = selected
    ? requiredEnv("EXPO_PUBLIC_API_BASE_URL").replace(/\/$/, "")
    : "https://invalid.local";

export default {
    android: {
        googleServicesFile: selected ? requiredEnv("GOOGLE_SERVICES_JSON") : "google-services.json",
    },
    extra: {
        AUTH_MODE: "google",
        FIREBASE_PROJECT_ID: "your-say-news-development",
        FIREBASE_API_KEY: "AIzaSyCuXzG1i1gOUn00N_JWNoKzyZJbFHViyCw",
        FIREBASE_APP_ID: "1:959119448962:android:34284785a6c9d4d8d9cd9b",
        GOOGLE_WEB_CLIENT_ID:
            "959119448962-ogj2vvtlqernrei3vnko48c4gqpgi5f3.apps.googleusercontent.com",
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
