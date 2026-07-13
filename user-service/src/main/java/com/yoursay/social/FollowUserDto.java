package com.yoursay.social;

/**
 * A single entry in a followers / following list: the public identity of a connected user
 * plus whether the viewer already follows them (so the list can render a follow button).
 */
public record FollowUserDto(
        Long id,
        String displayName,
        String handle,
        String avatarUrl,
        boolean followedByViewer
) {
}
