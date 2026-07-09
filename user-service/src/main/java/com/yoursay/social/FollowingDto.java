package com.yoursay.social;

import java.util.Set;

public record FollowingDto(Set<Long> userIds) {
}
