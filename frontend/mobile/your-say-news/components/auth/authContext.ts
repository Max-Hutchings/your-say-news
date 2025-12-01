import {createJSONStorage, persist} from "zustand/middleware";
import {Platform} from "react-native";
import {create} from "zustand";
import {User, UserState} from "@/components/auth/userState";
import * as SecureStore from "expo-secure-store";
import {KeycloakTokens, loginWithKeycloak} from "@/components/auth/keycloakService";
import {getUser} from "@/components/auth/UserService";


const isWeb = Platform.OS === "web";

export const useAuthStore = create(
    persist<UserState>(
        (set) => ({
            _stateHydrated: false,
            isLoggedIn: false,
            hasOnboarded: false,
            id: null,
            email: null,
            firstName: null,
            lastName: null,
            dateOfBirth: null,
            accessToken: null,
            refreshToken: null,
            accessTokenExpiresAt: null,

            // ✅ Just reference the helpers, don't reimplement them
            getAccessToken,
            setAccessToken,
            getRefreshToken,
            setRefreshToken,
            accessTokenExpired,

            login,
            logout,
            setHasOnboarded,
        }),
        {
            name: "auth-store",
            storage: isWeb
                ? createJSONStorage(() => localStorage)
                : createJSONStorage(() => ({
                    setItem: (key: string, value: string) => SecureStore.setItemAsync(key, value),
                    getItem: (key: string) => SecureStore.getItemAsync(key),
                    removeItem: (key: string) => SecureStore.deleteItemAsync(key),
                })),
            onRehydrateStorage: () => {
                return (state) => {
                    if (state) {
                        state._stateHydrated = true;
                    }
                };
            },
        }
    )
);

// Your helpers – still with no implementation as you wanted
function getAccessToken(): string | null  {
    const state = useAuthStore.getState();
    return state.accessToken;
}

function setAccessToken(token: string): void {
    useAuthStore.setState({
        accessToken: token,
    });
}

function getRefreshToken(): string | null {
    const state = useAuthStore.getState();
    return state.refreshToken;
}

function setRefreshToken(token: string): void {
    useAuthStore.setState({
        refreshToken: token,
    });
}

async function login(): Promise<boolean> {
    console.log("Login Called")
    // Ask KeycloakService to run the PKCE flow and give us tokens
    const tokens: KeycloakTokens | null = await loginWithKeycloak();
    console.log(tokens);

    if (!tokens) {
        return false; // user cancelled / error
    }

    useAuthStore.setState({
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
    })


    // Fetch user details
    const user: User | null = await getUser()

    if (!user){
        return false;
    }


    // Once we have tokens, update your Zustand state
    useAuthStore.setState({
        id: user.id,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        dateOfBirth: user.dateOfBirth,
        isLoggedIn: true,

    });
    console.log(useAuthStore.getState())

    return true;
}




function logout(): void{
    useAuthStore.setState({
        email: null,
        firstName: null,
        lastName: null,
        dateOfBirth: null,
        accessToken: null,
        refreshToken: null,
        isLoggedIn: false,
    })
}

function accessTokenExpired(): boolean{
    return useAuthStore.getState().accessTokenExpired();
}


function setHasOnboarded(onboarded: boolean): void{
     useAuthStore.setState({
        hasOnboarded: onboarded
    })
}