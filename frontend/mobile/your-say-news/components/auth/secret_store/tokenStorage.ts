import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

export class TokenStore {
    // Detect if running on web
    private static readonly isWeb = Platform.OS === "web";

    // Retrieve a token by key
    static async getItemAsync(key: string): Promise<string | null> {
        if (TokenStore.isWeb) {
            // Web fallback (local dev only)
            return localStorage.getItem(key);
        }

        // Native secure storage
        return await SecureStore.getItemAsync(key);
    }

    // Save a token under a key
    static async setItemAsync(key: string, value: string): Promise<void> {
        if (TokenStore.isWeb) {
            localStorage.setItem(key, value);
            return;
        }

        await SecureStore.setItemAsync(key, value);
    }

    // Delete a stored key
    static async deleteItemAsync(key: string): Promise<void> {
        if (TokenStore.isWeb) {
            localStorage.removeItem(key);
            return;
        }

        await SecureStore.deleteItemAsync(key);
    }
}
