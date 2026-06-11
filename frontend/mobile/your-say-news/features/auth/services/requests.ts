
import axios, {AxiosInstance, InternalAxiosRequestConfig} from "axios";
import {TokenStore} from "./tokenStorage";

/**
 * All HTTP requests from Your Say News frontend should be sent from an instance within this class.
 */
export default class YsnHttpClient{
    private static authIncludedInstance: AxiosInstance | null = null;
    private static authExcludedInstance: AxiosInstance;

    /**
     * For secure HTTP requests that include a bearer with the user's access token
     */
    public static getSecure(): AxiosInstance {
        if (!this.authIncludedInstance) {
            this.authIncludedInstance = axios.create({
                baseURL: "", // set backend URL here or inject it
            });

            this.authIncludedInstance.interceptors.request.use(
                async (config: InternalAxiosRequestConfig) => {
                    const persisted = await TokenStore.getItemAsync("auth-store");

                    if (!persisted) {
                        return config; // no token, no Authorization header
                    }

                    // Parse the stored Zustand persist JSON
                    const parsed = JSON.parse(persisted);

                    // Zustand persist stores your state under the "state" property
                    const token = parsed?.state?.accessToken;

                    if (token) {
                        config.headers.Authorization = `Bearer ${token}`;
                    }

                    return config;
                }
            );

        }

        return this.authIncludedInstance;
    }
}


