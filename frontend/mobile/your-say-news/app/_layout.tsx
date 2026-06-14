import {SplashScreen, Stack} from "expo-router";
import {useAuthStore} from "@/features/auth";
import {useEffect} from "react";
import {Platform} from "react-native";
import {SafeAreaProvider} from "react-native-safe-area-context";
import {useFonts} from "expo-font";
import {
    Newsreader_400Regular,
    Newsreader_400Regular_Italic,
    Newsreader_500Medium,
} from "@expo-google-fonts/newsreader";
import {
    SchibstedGrotesk_400Regular,
    SchibstedGrotesk_500Medium,
    SchibstedGrotesk_600SemiBold,
    SchibstedGrotesk_700Bold,
} from "@expo-google-fonts/schibsted-grotesk";
import {
    SplineSansMono_400Regular,
    SplineSansMono_500Medium,
    SplineSansMono_600SemiBold,
} from "@expo-google-fonts/spline-sans-mono";
import {ThemeProvider} from "@/constants/theme";

import "../global.css";

const isWeb = Platform.OS === "web";

if (!isWeb) {
    SplashScreen.preventAutoHideAsync();
}

export default function RootLayout(){

    const {isLoggedIn, _stateHydrated} = useAuthStore();

    // Editorial type system: Newsreader (serif), Schibsted Grotesk (UI), Spline Sans Mono (data).
    const [fontsLoaded] = useFonts({
        Newsreader_400Regular,
        Newsreader_400Regular_Italic,
        Newsreader_500Medium,
        SchibstedGrotesk_400Regular,
        SchibstedGrotesk_500Medium,
        SchibstedGrotesk_600SemiBold,
        SchibstedGrotesk_700Bold,
        SplineSansMono_400Regular,
        SplineSansMono_500Medium,
        SplineSansMono_600SemiBold,
    });

    const ready = _stateHydrated && fontsLoaded;

    useEffect(() => {
        if (ready) {
            SplashScreen.hideAsync();
        }
    }, [ready]);

    if (!ready && !isWeb) {
        return null;
    }

    return(
        <SafeAreaProvider>
            <ThemeProvider>
                <Stack screenOptions={{ headerShown: false }}>
                    <Stack.Protected guard={isLoggedIn}>
                        <Stack.Screen name="(protected)" />
                    </Stack.Protected>
                    <Stack.Protected guard={!isLoggedIn}>
                        <Stack.Screen name={"sign-in"}  />
                    </Stack.Protected>
                </Stack>
            </ThemeProvider>
        </SafeAreaProvider>
    )
}
