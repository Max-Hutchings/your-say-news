import {SplashScreen, Stack} from "expo-router";
import {useAuthStore} from "@/features/auth";
import {useEffect} from "react";
import {Platform} from "react-native";
import {ThemeProvider} from "@/constants/theme";

import "../global.css";

const isWeb = Platform.OS === "web";

if (!isWeb) {
    SplashScreen.preventAutoHideAsync();
}

export default function RootLayout(){

    const {isLoggedIn, _stateHydrated} = useAuthStore();

    useEffect(() => {
        if (_stateHydrated) {
            SplashScreen.hideAsync();
        }
    }, [_stateHydrated]);

    if (!_stateHydrated && !isWeb) {
        return null;
    }

    return(
        <ThemeProvider>
            <Stack>
                <Stack.Protected guard={isLoggedIn}>
                    <Stack.Screen name="(protected)" />
                </Stack.Protected>
                <Stack.Protected guard={!isLoggedIn}>
                    <Stack.Screen name={"sign-in"}  />
                </Stack.Protected>
            </Stack>
        </ThemeProvider>
    )
}