package com.yoursay.user.social;

import java.util.Set;

public record FollowingDto(Set<Long> userIds) {
}
