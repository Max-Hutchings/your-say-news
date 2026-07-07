import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { SentimentBreakdown } from "../types";

/**
 * Reads aggregated sentiment for a post from post-service over the shared authenticated HTTP
 * client (bearer + refresh handled by YsnHttpClient). Both endpoints are gated server-side: they
 * only succeed once the caller has voted on the post, otherwise 403 — surfaced to the caller as a
 * thrown error the hook classifies as `notVoted`.
 *
 * Counts + percentages only: nothing here ever identifies who cast a vote.
 */

const extra = Constants.expoConfig?.extra ?? {};
const VOTES_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/votes`;

/** Overall yes/no split for a post's support question. Throws on 403 (not voted) / 4xx / 5xx. */
export async function getOverallSentiment(postId: number): Promise<SentimentBreakdown> {
  const { data } = await YsnHttpClient.getSecure().get<SentimentBreakdown>(
    `${VOTES_URL}/${postId}/sentiment`
  );
  return data;
}

/**
 * The yes/no split broken down by one characteristic `axis` (a backend field name, e.g.
 * `politicalPersuasion`). Buckets arrive largest-first. Throws on 403 (not voted) / 4xx / 5xx.
 */
export async function getAxisSentiment(
  postId: number,
  axis: string
): Promise<SentimentBreakdown> {
  const { data } = await YsnHttpClient.getSecure().get<SentimentBreakdown>(
    `${VOTES_URL}/${postId}/sentiment/${axis}`
  );
  return data;
}
