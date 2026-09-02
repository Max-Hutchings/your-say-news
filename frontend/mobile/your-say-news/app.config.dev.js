// app.config.dev.js
const postServiceHost = requiredEnv("EXPO_PUBLIC_POST_SERVICE_HOST");
const postServicePort = requiredEnv("EXPO_PUBLIC_POST_SERVICE_PORT");

export default {
    extra: {
        FIREBASE_PROJECT_ID: "demo-your-say-news",
        FIREBASE_API_KEY: "local-firebase-emulator-key",
        FIREBASE_APP_ID: "1:123456789:web:local-your-say-news",
        FIREBASE_AUTH_EMULATOR_URL:
            process.env.EXPO_PUBLIC_AUTH_BASE_URL ?? "http://localhost:9099",

        USER_SERVICE_HOST: postServiceHost,
        USER_SERVICE_PORT: postServicePort,

        POST_SERVICE_HOST: postServiceHost,
        POST_SERVICE_PORT: postServicePort,

        // User characteristics now run inside post-service during the transition.
        CHARACTERISTIC_SERVICE_HOST: postServiceHost,
        CHARACTERISTIC_SERVICE_PORT: postServicePort
    }
};

function requiredEnv(name) {
    const value = process.env[name];
    if (!value) {
        throw new Error(`Missing required Expo environment variable: ${name}`);
    }
    return value;
}
