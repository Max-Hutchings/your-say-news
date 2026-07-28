package com.yoursay.user.user.dto;

public record PublicProfileDto(
        Long id,
        String displayName,
        String handle,
        String avatarUrl,
        long followerCount,
        long followingCount,
        boolean followedByViewer
) {
}
