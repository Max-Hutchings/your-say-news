import Constants from "expo-constants";
import { YsnHttpClient } from "@/features/auth";
import type { ConnectionsTab, FollowPage, FollowStatus, PublicProfile } from "../types";

const extra = Constants.expoConfig?.extra ?? {};
const USER_URL = `${extra.USER_SERVICE_HOST}${extra.USER_SERVICE_PORT}`;

export const CONNECTIONS_PAGE_SIZE = 50;

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

export async function listConnections(
  userId: number,
  tab: ConnectionsTab,
  page: number,
  size: number = CONNECTIONS_PAGE_SIZE,
): Promise<FollowPage> {
  const { data } = await YsnHttpClient.getSecure().get<FollowPage>(
    `${USER_URL}/social/${userId}/${tab}?page=${page}&size=${size}`,
  );
  return data;
}
