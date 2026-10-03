type AsyncStorageLike = {
    getItem(key: string): Promise<string | null>;
    setItem(key: string, value: string): Promise<unknown>;
};

test("native Firebase uses persistent auth and the Android emulator host", async () => {
    jest.resetModules();
    const namedApp = { name: "your-say-news-auth" };
    const auth = { currentUser: null };
    const persistence = { type: "async-storage" };
    const initializeApp = jest.fn(() => namedApp);
    const initializeAuth = jest.fn(() => auth);
    const getReactNativePersistence = jest.fn((_storage: AsyncStorageLike) => persistence);
    const connectAuthEmulator = jest.fn();
    jest.doMock("expo-constants", () => ({
        __esModule: true,
        default: {
            expoConfig: {
                extra: { FIREBASE_AUTH_EMULATOR_URL: "http://localhost:9099" },
            },
        },
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
    const asyncStorageMock = getReactNativePersistence.mock.calls[0][0];
    const storedFirebaseSession = JSON.stringify({ uid: "riley-reader" });

    await asyncStorageMock.setItem("firebase:authUser:demo-your-say-news", storedFirebaseSession);

    expect(initializeApp).toHaveBeenCalledWith({
        apiKey: "local-firebase-emulator-key",
        authDomain: "demo-your-say-news.firebaseapp.com",
        projectId: "demo-your-say-news",
        appId: "1:123456789:web:local-your-say-news",
    }, "your-say-news-auth");
    await expect(asyncStorageMock.getItem("firebase:authUser:demo-your-say-news"))
        .resolves.toBe(storedFirebaseSession);
    expect(initializeAuth).toHaveBeenCalledWith(namedApp, { persistence });
    expect(connectAuthEmulator).toHaveBeenCalledWith(
        auth,
        "http://10.0.2.2:9099",
        { disableWarnings: true },
    );
});
