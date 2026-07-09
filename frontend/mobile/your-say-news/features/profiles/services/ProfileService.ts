import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { FollowStatus, PublicProfile } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const USER_URL = `${extra.USER_SERVICE_HOST}${extra.USER_SERVICE_PORT}`;

export async function getMyProfile(): Promise<PublicProfile | null> {
  const res = await YsnHttpClient.getSecure().get<PublicProfile>(`${USER_URL}/profiles/me`);
  return res.status === 204 ? null : res.data;
}

export async function getProfile(userId: number): Promise<PublicProfile | null> {
  const res = await YsnHttpClient.getSecure().get<PublicProfile>(`${USER_URL}/profiles/${userId}`);
  return res.status === 204 ? null : res.data;
}

export async function followUser(userId: number): Promise<FollowStatus> {
  const { data } = await YsnHttpClient.getSecure().post<FollowStatus>(`${USER_URL}/social/follows/${userId}`);
  return data;
}

export async function unfollowUser(userId: number): Promise<FollowStatus> {
  const { data } = await YsnHttpClient.getSecure().delete<FollowStatus>(`${USER_URL}/social/follows/${userId}`);
  return data;
}
