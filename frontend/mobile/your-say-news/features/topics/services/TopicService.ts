import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { TopicTag } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const TOPIC_TAGS_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/topic-tags`;

/** Active topics in catalogue order. */
export async function listTopicTags(): Promise<TopicTag[]> {
  const { data } = await YsnHttpClient.getSecure().get<TopicTag[]>(TOPIC_TAGS_URL);
  return data ?? [];
}
