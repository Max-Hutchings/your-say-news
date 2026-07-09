package com.yoursay.social;

public record FollowStatusDto(
        Long userId,
        boolean following,
        long followerCount,
        long followingCount
) {
}
