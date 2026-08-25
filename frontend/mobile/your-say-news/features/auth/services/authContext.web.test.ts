/**
 * The web half of session wiping.
 *
 * `authContext` decides `isWeb` once at module load, and the default jest preset reports a native
 * platform — so the browser-storage branch of `wipeSession` never runs in `authContext.test.ts`.
 * That branch is the whole point of this feature ("no localStorage or cookies left to clear by
 * hand"), so it gets its own file with the platform forced to web before the store is imported.
 */
const storageStub = () => {
    let entries = new Map<string, string>();
    return {
        get size() {
            return entries.size;
        },
        getItem: (key: string) => entries.get(key) ?? null,
        setItem: (key: string, value: string) => {
            entries.set(key, value);
        },
        removeItem: (key: string) => {
            entries.delete(key);
        },
        clear: () => {
            entries = new Map();
        },
    };
};

const localStorageStub = storageStub();
const sessionStorageStub = storageStub();

Object.defineProperty(globalThis, "localStorage", { value: localStorageStub, writable: true });
Object.defineProperty(globalThis, "sessionStorage", { value: sessionStorageStub, writable: true });

let cookieJar = "";
Object.defineProperty(globalThis, "document", {
    value: {
        get cookie() {
            return cookieJar;
        },
        set cookie(value: string) {
            // Only record expiries — that is all the assertions need to see.
            cookieJar = value.includes("01 Jan 1970") ? "" : value;
        },
    },
    writable: true,
});

jest.mock("react-native", () => ({ Platform: { OS: "web" } }));

jest.mock("expo-secure-store", () => ({
    getItemAsync: jest.fn(async () => null),
    setItemAsync: jest.fn(async () => undefined),
    deleteItemAsync: jest.fn(async () => undefined),
}));

jest.mock("./keycloakService", () => ({
    loginWithKeycloak: jest.fn(),
    refreshTokens: jest.fn(),
    revokeTokens: jest.fn(async () => undefined),
}));

jest.mock("./UserService", () => ({
    getUser: jest.fn(),
    getOnboardingStatus: jest.fn(),
    verifySession: jest.fn(),
}));

// Required rather than imported: `authContext` reads `Platform.OS` and resolves its web storage at
// module load, and a static import would be hoisted above the globals stubbed out above.
type AuthStore = typeof import("./authContext").useAuthStore;
let useAuthStore: AuthStore;
let mockVerifySession: jest.Mock;

beforeAll(() => {
    useAuthStore = require("./authContext").useAuthStore;
    mockVerifySession = require("./UserService").verifySession as jest.Mock;
});

beforeEach(() => {
    jest.clearAllMocks();
    localStorageStub.clear();
    sessionStorageStub.clear();
    cookieJar = "";
});

describe("restoreSession on web", () => {
    it("empties browser storage and expires cookies when the stored session is rejected", async () => {
        localStorage.setItem("auth-store", JSON.stringify({ state: { email: "riley.reader@example.com" } }));
        localStorage.setItem("unrelated-key", "still-here");
        sessionStorage.setItem("ysn-keycloak-auth-session", "pkce-state");
        document.cookie = "KEYCLOAK_SESSION=abc123";

        useAuthStore.setState({
            id: 10,
            email: "riley.reader@example.com",
            isLoggedIn: true,
            hasCharacteristics: true,
            accessToken: "expired-access",
            refreshToken: "expired-refresh",
        });
        mockVerifySession.mockResolvedValue({ state: "unauthenticated" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("signed-out");

        // Nothing identifying survives in the browser — this is what stops a dead session being
        // handed to whoever opens the app next, with no manual clearing.
        expect(localStorage.size).toBe(0);
        expect(localStorage.getItem("auth-store")).toBeNull();
        expect(sessionStorage.size).toBe(0);
        expect(document.cookie).toBe("");

        const state = useAuthStore.getState();
        expect(state.isLoggedIn).toBe(false);
        expect(state.email).toBeNull();
        expect(state.accessToken).toBeNull();
    });

    it("leaves browser storage untouched when the server could not be reached", async () => {
        localStorage.setItem("auth-store", JSON.stringify({ state: { email: "riley.reader@example.com" } }));
        document.cookie = "KEYCLOAK_SESSION=abc123";

        useAuthStore.setState({
            isLoggedIn: true,
            accessToken: "stored-access",
            refreshToken: "stored-refresh",
        });
        mockVerifySession.mockResolvedValue({ state: "unreachable" });

        await expect(useAuthStore.getState().restoreSession()).resolves.toBe("unverified");

        expect(localStorage.getItem("auth-store")).not.toBeNull();
        expect(document.cookie).toBe("KEYCLOAK_SESSION=abc123");
        expect(useAuthStore.getState().accessToken).toBe("stored-access");
        expect(useAuthStore.getState().isLoggedIn).toBe(false);
    });
});
