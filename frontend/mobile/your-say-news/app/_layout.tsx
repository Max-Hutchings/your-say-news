import {router, SplashScreen, Stack} from "expo-router";
import {useAuth} from "@/components/old_auth/AuthContext";
import {useAuthStore} from "@/components/auth/authContext";
import {useEffect} from "react";
import {Platform} from "react-native";

const isWeb = Platform.OS === "web";

if (!isWeb) {
    SplashScreen.preventAutoHideAsync();
}

export default function RootLayout(){

    const {isLoggedIn, _stateHydrated} = useAuthStore();
    console.log("Root Layout: " + isLoggedIn);

    useEffect(() => {
        if (_stateHydrated) {
            SplashScreen.hideAsync();
        }
    }, [_stateHydrated]);

    if (!_stateHydrated && !isWeb) {
        return null;
    }

    return(
        <Stack>
            <Stack.Protected guard={isLoggedIn}>
                <Stack.Screen name="(protected)" />
            </Stack.Protected>
            <Stack.Protected guard={!isLoggedIn}>
                <Stack.Screen name={"sign-in"}  />

            </Stack.Protected>

        </Stack>
    )
}