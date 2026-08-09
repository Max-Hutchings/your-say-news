package com.yoursay.user.social.dto;

import java.util.Set;

public record FollowingDto(Set<Long> userIds) {
}
