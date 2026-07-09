package com.yoursay.social;

import java.util.Set;

public interface SocialService {

    FollowStatusDto follow(String followerEmail, long followedUserId);

    FollowStatusDto unfollow(String followerEmail, long followedUserId);

    FollowStatusDto getStatus(String viewerEmail, long userId);

    Set<Long> getFollowingUserIds(String viewerEmail);

    boolean isFollowing(long followerUserId, long followedUserId);

    long countFollowers(long userId);

    long countFollowing(long userId);
}
