export interface PublicProfile {
  id: number;
  displayName: string;
  handle: string;
  avatarUrl: string | null;
  followerCount: number;
  followingCount: number;
  followedByViewer: boolean;
}

export interface FollowStatus {
  userId: number;
  following: boolean;
  followerCount: number;
  followingCount: number;
}
