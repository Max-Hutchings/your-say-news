package com.yoursay.user.social.dto;

public record FollowStatusDto(
        Long userId,
        boolean following,
        long followerCount,
        long followingCount
) {
}
