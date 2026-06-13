export type User = {
    id: number | null,
    email: string | null;
    firstName: string | null;
    lastName: string | null;
    // ISO-8601 date string as returned by the API (e.g. "1990-05-21"), not a Date instance.
    dateOfBirth: string | null;
};

// Use interface if you want to "extend"
export interface UserState extends User {
    _stateHydrated: boolean;
    isLoggedIn: boolean;
    hasOnboarded: boolean;

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
    logout: () => Promise<void>;
    setHasOnboarded: (onboarded: boolean) => void;
}
