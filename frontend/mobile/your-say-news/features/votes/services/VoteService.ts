import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { Vote } from "../types";

/**
 * Talks to post-service for voting over the shared authenticated HTTP client (bearer + token
 * refresh handled by YsnHttpClient). The voter is resolved server-side from the token, so we
 * never send a userId — only the postId and the stance.
 */

const extra = Constants.expoConfig?.extra ?? {};
const VOTES_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/votes`;

/** Cast a vote on a post's support question. Returns the created vote; throws on 409/4xx/5xx. */
export async function castVote(postId: number, optionId: number): Promise<Vote> {
  const { data } = await YsnHttpClient.getSecure().post<Vote>(VOTES_URL, { postId, optionId });
  return data;
}

/**
 * The caller's existing vote on a post, or null when they have not voted. The service returns
 * 204 (no body) in the not-voted case, which we surface as null rather than a thrown error.
 */
export async function getMyVote(postId: number): Promise<Vote | null> {
  const res = await YsnHttpClient.getSecure().get<Vote>(`${VOTES_URL}/${postId}/mine`);
  if (res.status === 204 || !res.data) {
    return null;
  }
  return res.data;
}
