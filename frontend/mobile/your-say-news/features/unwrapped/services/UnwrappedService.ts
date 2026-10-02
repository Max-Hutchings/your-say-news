import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { FollowUpResponse, UnwrappedFeatures, UnwrappedResponse, UnwrapRequest } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const SERVICE_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}`;
const POSTS_URL = `${SERVICE_URL}/posts`;

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

export async function getUnwrappedFeatures(): Promise<UnwrappedFeatures> {
  const { data } = await YsnHttpClient.getSecure().get<UnwrappedFeatures>(
    `${SERVICE_URL}/unwrapped/features`
  );
  return data;
}

/** Queues generation for the post when it has enough votes; the backend decides. */
export async function requestUnwrap(postId: number): Promise<UnwrapRequest> {
  const { data } = await YsnHttpClient.getSecure().post<UnwrapRequest>(
    `${POSTS_URL}/${postId}/unwrapped/generate`
  );
  return data;
}
