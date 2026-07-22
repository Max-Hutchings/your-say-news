package com.yoursay.user.social;

public record FollowStatusDto(
        Long userId,
        boolean following,
        long followerCount,
        long followingCount
) {
}
