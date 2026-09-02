import { FIREBASE_AUTH_APP_NAME, firebaseClientConfig } from "./firebaseClientConfig";

test("uses valid local emulator defaults when Expo runtime config is unavailable", () => {
    expect(firebaseClientConfig({
        FIREBASE_PROJECT_ID: "   ",
        FIREBASE_API_KEY: "",
        FIREBASE_APP_ID: null,
        FIREBASE_AUTH_EMULATOR_URL: 9099,
    })).toEqual({
        options: {
            apiKey: "local-firebase-emulator-key",
            authDomain: "demo-your-say-news.firebaseapp.com",
            projectId: "demo-your-say-news",
            appId: "1:123456789:web:local-your-say-news",
        },
        emulatorUrl: "http://localhost:9099",
    });
    expect(FIREBASE_AUTH_APP_NAME).toBe("your-say-news-auth");
});

test("uses non-empty Expo Firebase configuration", () => {
    expect(firebaseClientConfig({
        FIREBASE_PROJECT_ID: " configured-project ",
        FIREBASE_API_KEY: " configured-key ",
        FIREBASE_APP_ID: " configured-app ",
        FIREBASE_AUTH_EMULATOR_URL: " http://127.0.0.1:9099 ",
    })).toEqual({
        options: {
            apiKey: "configured-key",
            authDomain: "configured-project.firebaseapp.com",
            projectId: "configured-project",
            appId: "configured-app",
        },
        emulatorUrl: "http://127.0.0.1:9099",
    });
});
