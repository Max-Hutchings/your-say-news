export type User = {
    id: number | null,
    email: string | null;
    firstName: string | null;
    lastName: string | null;
    // ISO-8601 date string as returned by the API (e.g. "1990-05-21"), not a Date instance.
    dateOfBirth: string | null;
    // ISO-8601 timestamp of privacy-promise consent, or null if they have not consented yet.
    consentedAt: string | null;
    accountType: "STANDARD" | "OFFICIAL";
    publisherStatus: "NONE" | "ACTIVE" | "SUSPENDED";
    canPublish: boolean;
};

/** Server's view of how far the user is through onboarding (GET /your-say-user/onboarding). */
export type OnboardingStatus = {
    consented: boolean;
    hasCharacteristics: boolean;
    onboarded: boolean;
};

// Use interface if you want to "extend"
export interface UserState extends User {
    _stateHydrated: boolean;
    isLoggedIn: boolean;
    hasOnboarded: boolean;
    // Whether the user already has a saved characteristic profile — drives routing so a returning
    // user who has filled the wizard is never sent back through it.
    hasCharacteristics: boolean;

    accessToken: string | null;
    refreshToken: string | null;

    getAccessToken: () => string | null;
    setAccessToken: (token: string) => void;

    getRefreshToken: () => string | null;
    setRefreshToken: (token: string) => void;

    accessTokenExpiresAt: number | null;
    accessTokenExpired: () => boolean;
    refreshAccessToken: () => Promise<string | null>;

    login: () => Promise<boolean>; // true/false for success
    completeLogin: (tokens: {
        accessToken: string;
        refreshToken: string | null;
        expiresIn: number | null;
    }) => Promise<boolean>;
    logout: () => Promise<void>;
    setHasOnboarded: (onboarded: boolean) => void;
    setHasCharacteristics: (has: boolean) => void;
    setConsentedAt: (at: string | null) => void;
}
