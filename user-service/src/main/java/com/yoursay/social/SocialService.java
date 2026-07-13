package com.yoursay.social;

import java.util.Set;

public interface SocialService {

    FollowStatusDto follow(String followerEmail, long followedUserId);

    FollowStatusDto unfollow(String followerEmail, long followedUserId);

    FollowStatusDto getStatus(String viewerEmail, long userId);

    Set<Long> getFollowingUserIds(String viewerEmail);

    /** One page of the users who follow {@code userId}, newest first. */
    FollowPageDto listFollowers(String viewerEmail, long userId, int page, int size);

    /** One page of the users {@code userId} follows, newest first. */
    FollowPageDto listFollowing(String viewerEmail, long userId, int page, int size);

    boolean isFollowing(long followerUserId, long followedUserId);

    long countFollowers(long userId);

    long countFollowing(long userId);
}
