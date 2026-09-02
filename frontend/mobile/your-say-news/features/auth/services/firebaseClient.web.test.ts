describe("Firebase web client", () => {
    beforeEach(() => {
        jest.resetModules();
    });

    test("reuses the named auth app and connects it to the configured emulator", () => {
        const namedApp = { name: "your-say-news-auth" };
        const defaultApp = { name: "[DEFAULT]" };
        const auth = { currentUser: null };
        const initializeApp = jest.fn();
        const getAuth = jest.fn(() => auth);
        const connectAuthEmulator = jest.fn();
        mockExpoConfig("http://127.0.0.1:9099");
        jest.doMock("firebase/app", () => ({
            getApps: jest.fn(() => [defaultApp, namedApp]),
            initializeApp,
        }));
        jest.doMock("firebase/auth", () => ({ getAuth, connectAuthEmulator }));

        jest.isolateModules(() => require("./firebaseClient.web"));

        expect(initializeApp).not.toHaveBeenCalled();
        expect(getAuth).toHaveBeenCalledWith(namedApp);
        expect(connectAuthEmulator).toHaveBeenCalledWith(
            auth,
            "http://127.0.0.1:9099",
            { disableWarnings: true },
        );
    });

    test("creates the named app when Firebase has no existing auth app", () => {
        const namedApp = { name: "your-say-news-auth" };
        const auth = { currentUser: null };
        const initializeApp = jest.fn(() => namedApp);
        const getAuth = jest.fn(() => auth);
        const connectAuthEmulator = jest.fn();
        mockExpoConfig("http://localhost:9099");
        jest.doMock("firebase/app", () => ({
            getApps: jest.fn(() => []),
            initializeApp,
        }));
        jest.doMock("firebase/auth", () => ({ getAuth, connectAuthEmulator }));

        jest.isolateModules(() => require("./firebaseClient.web"));

        expect(initializeApp).toHaveBeenCalledWith({
            apiKey: "local-firebase-emulator-key",
            authDomain: "demo-your-say-news.firebaseapp.com",
            projectId: "demo-your-say-news",
            appId: "1:123456789:web:local-your-say-news",
        }, "your-say-news-auth");
        expect(getAuth).toHaveBeenCalledWith(namedApp);
    });
});

function mockExpoConfig(emulatorUrl: string): void {
    jest.doMock("expo-constants", () => ({
        __esModule: true,
        default: {
            expoConfig: {
                extra: {
                    FIREBASE_PROJECT_ID: "demo-your-say-news",
                    FIREBASE_API_KEY: "local-firebase-emulator-key",
                    FIREBASE_APP_ID: "1:123456789:web:local-your-say-news",
                    FIREBASE_AUTH_EMULATOR_URL: emulatorUrl,
                },
            },
        },
    }));
}
