import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { CreatePostInput, Post } from "../types";

/**
 * Talks to post-service for post CRUD over the shared authenticated HTTP
 * client (bearer + token refresh handled by YsnHttpClient). The author is
 * resolved server-side from the token, so we never send a userId.
 */

const extra = Constants.expoConfig?.extra ?? {};
const POSTS_URL = `${extra.POST_SERVICE_HOST}${extra.POST_SERVICE_PORT}/posts`;

/** Create a post; returns the created PostDto. */
export async function createPost(input: CreatePostInput): Promise<Post> {
  const { data } = await YsnHttpClient.getSecure().post<Post>(POSTS_URL, input);
  return data;
}

/**
 * Fetch a single post by id. The service returns 204 (no body) when the post
 * doesn't exist, which we surface as null rather than a thrown error.
 */
export async function getPost(id: number): Promise<Post | null> {
  const res = await YsnHttpClient.getSecure().get<Post>(`${POSTS_URL}/${id}`);
  if (res.status === 204 || !res.data) {
    return null;
  }
  return res.data;
}

/** List a user's posts, newest first. */
export async function listByUser(userId: number): Promise<Post[]> {
  const { data } = await YsnHttpClient.getSecure().get<Post[]>(`${POSTS_URL}/user/${userId}`);
  return data ?? [];
}

/** Recent posts across all authors, newest first (interim feed). */
export async function getRecent(): Promise<Post[]> {
  const { data } = await YsnHttpClient.getSecure().get<Post[]>(POSTS_URL);
  return data ?? [];
}
