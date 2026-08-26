test("native Firebase uses persistent auth and the Android emulator host", () => {
    jest.resetModules();
    const namedApp = { name: "your-say-news-auth" };
    const auth = { currentUser: null };
    const storage = { getItem: jest.fn(), setItem: jest.fn() };
    const persistence = { storage };
    const initializeApp = jest.fn(() => namedApp);
    const initializeAuth = jest.fn(() => auth);
    const getReactNativePersistence = jest.fn(() => persistence);
    const connectAuthEmulator = jest.fn();
    jest.doMock("expo-constants", () => ({
        __esModule: true,
        default: {
            expoConfig: {
                extra: { FIREBASE_AUTH_EMULATOR_URL: "http://localhost:9099" },
            },
        },
    }));
    jest.doMock("@react-native-async-storage/async-storage", () => ({
        __esModule: true,
        default: storage,
    }));
    jest.doMock("firebase/app", () => ({
        getApps: jest.fn(() => []),
        initializeApp,
    }));
    jest.doMock("firebase/auth", () => ({
        connectAuthEmulator,
        initializeAuth,
        getReactNativePersistence,
    }));

    jest.isolateModules(() => require("./firebaseClient.native"));

    expect(initializeApp).toHaveBeenCalledWith({
        apiKey: "local-firebase-emulator-key",
        authDomain: "demo-your-say-news.firebaseapp.com",
        projectId: "demo-your-say-news",
        appId: "1:123456789:web:local-your-say-news",
    }, "your-say-news-auth");
    expect(getReactNativePersistence).toHaveBeenCalledWith(storage);
    expect(initializeAuth).toHaveBeenCalledWith(namedApp, { persistence });
    expect(connectAuthEmulator).toHaveBeenCalledWith(
        auth,
        "http://10.0.2.2:9099",
        { disableWarnings: true },
    );
});
