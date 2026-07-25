import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { FollowUpResponse, UnwrappedResponse } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const POSTS_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/posts`;

export async function getUnwrapped(postId: number): Promise<UnwrappedResponse> {
  const { data } = await YsnHttpClient.getSecure().get<UnwrappedResponse>(
    `${POSTS_URL}/${postId}/unwrapped`
  );
  return data;
}

export async function submitFollowUp(
  postId: number,
  storyId: string,
  optionId: number
): Promise<FollowUpResponse> {
  const { data } = await YsnHttpClient.getSecure().post<FollowUpResponse>(
    `${POSTS_URL}/${postId}/unwrapped/${storyId}/follow-up`,
    { optionId }
  );
  return data;
}
