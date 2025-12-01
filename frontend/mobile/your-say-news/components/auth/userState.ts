export type User = {
    id: number | null,
    email: string | null;
    firstName: string | null;
    lastName: string | null;
    dateOfBirth: Date | null;
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

    login: () => Promise<boolean>; // true/false for success
    logout: () => void;
    setHasOnboarded: (onboarded: boolean) => void;
}
