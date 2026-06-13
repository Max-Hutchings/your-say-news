// app.config.prod.js
export default {
    extra: {
        KEYCLOAK_BASE_URL: "http://localhost:8080",
        KEYCLOAK_REALM: "your-say-news",
        KEYCLOAK_CLIENT_ID: "frontend-client",

        ACCESS_TOKEN_KEY: "access_token",
        REFRESH_TOKEN_KEY: "refresh_token",
        ACCESS_TOKEN_EXPIRES: "access_token_expires",

        USER_SERVICE_HOST: "http://localhost:",
        USER_SERVICE_PORT: "8081",

        POST_SERVICE_HOST: "http://localhost:",
        POST_SERVICE_PORT: "8082",

        // Characteristic service does not exist yet — routed at user-service for now.
        CHARACTERISTIC_SERVICE_HOST: "http://localhost:",
        CHARACTERISTIC_SERVICE_PORT: "8081"
    }
};
