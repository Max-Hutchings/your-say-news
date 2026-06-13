
import axios, {AxiosInstance, InternalAxiosRequestConfig} from "axios";
import {useAuthStore} from "./authContext";

// Axios tags the original request config on retry so we only retry a 401 once.
type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

/**
 * All HTTP requests from Your Say News frontend should be sent from an instance within this class.
 *
 * The secure instance reads the access token straight from the in-memory auth store,
 * refreshes it proactively when it has expired, and retries once on a 401.
 */
export default class YsnHttpClient{
    private static authIncludedInstance: AxiosInstance | null = null;

    /**
     * For secure HTTP requests that include a bearer with the user's access token.
     */
    public static getSecure(): AxiosInstance {
        if (this.authIncludedInstance) {
            return this.authIncludedInstance;
        }

        const instance = axios.create({
            baseURL: "", // set backend URL here or inject it
        });

        // Attach the bearer, refreshing first if the current token has expired.
        instance.interceptors.request.use(
            async (config: InternalAxiosRequestConfig) => {
                const state = useAuthStore.getState();

                let token = state.accessToken;
                if (state.accessTokenExpired()) {
                    token = await state.refreshAccessToken();
                }

                if (token) {
                    config.headers.Authorization = `Bearer ${token}`;
                }

                return config;
            },
        );

        // If a request still 401s (token revoked mid-flight), refresh once and retry.
        instance.interceptors.response.use(
            (response) => response,
            async (error) => {
                const original = error.config as RetryableConfig | undefined;

                if (error.response?.status === 401 && original && !original._retry) {
                    original._retry = true;
                    const token = await useAuthStore.getState().refreshAccessToken();
                    if (token) {
                        original.headers.Authorization = `Bearer ${token}`;
                        return instance(original);
                    }
                }

                return Promise.reject(error);
            },
        );

        this.authIncludedInstance = instance;
        return instance;
    }
}
