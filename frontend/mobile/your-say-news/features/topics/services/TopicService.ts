import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { Topic } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const TOPICS_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/topics`;

/** Active topics in catalogue order. */
export async function listTopics(): Promise<Topic[]> {
  const { data } = await YsnHttpClient.getSecure().get<Topic[]>(TOPICS_URL);
  return data ?? [];
}
