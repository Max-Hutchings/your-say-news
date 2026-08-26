import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import { getFirebaseIdToken } from "./firebaseService";

type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

export default class YsnHttpClient {
    private static authIncludedInstance: AxiosInstance | null = null;

    public static getSecure(): AxiosInstance {
        if (this.authIncludedInstance) {
            return this.authIncludedInstance;
        }

        const instance = axios.create({ baseURL: "" });
        instance.interceptors.request.use(async (config) => {
            const token = await getFirebaseIdToken();
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
            return config;
        });
        instance.interceptors.response.use(
            (response) => response,
            async (error) => {
                const original = error.config as RetryableConfig | undefined;
                if (error.response?.status === 401 && original && !original._retry) {
                    original._retry = true;
                    const token = await getFirebaseIdToken(true);
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
