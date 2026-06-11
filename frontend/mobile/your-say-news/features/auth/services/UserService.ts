
import YsnHttpClient from "./requests";
import Constants from "expo-constants";
import {User} from "../types";


export async function getUser(): Promise<User | null> {
    try {

        const host: string = Constants.expoConfig?.extra?.USER_SERVICE_HOST
        const port: string = Constants.expoConfig?.extra?.USER_SERVICE_PORT
        console.log(host);
        const response = await YsnHttpClient.getSecure().get(host + port + "/your-say-user");
        console.log(response);
        if (response.status === 200) {
            return response.data; // already typed as User
        }

        console.error("Failed to authenticate user:", response.statusText);
        return null;

    } catch (err: any) {
        console.error("Network/request error:", err.message);
        return null;
    }
}
