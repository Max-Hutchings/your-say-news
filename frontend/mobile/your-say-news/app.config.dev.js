// app.config.dev.js
const postServiceHost = requiredEnv("EXPO_PUBLIC_POST_SERVICE_HOST");
const postServicePort = requiredEnv("EXPO_PUBLIC_POST_SERVICE_PORT");

export default {
    extra: {
        KEYCLOAK_BASE_URL: "http://localhost:8080",
        KEYCLOAK_REALM: "your-say-news",
        KEYCLOAK_CLIENT_ID: "frontend-client",

        ACCESS_TOKEN_KEY: "access_token",
        REFRESH_TOKEN_KEY: "refresh_token",
        ACCESS_TOKEN_EXPIRES: "access_token_expires",

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
