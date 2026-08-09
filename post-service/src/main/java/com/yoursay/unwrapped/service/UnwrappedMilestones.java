package com.yoursay.unwrapped.service;

import java.util.List;

final class UnwrappedMilestones {
    private static final List<Integer> VALUES = List.of(100, 250, 500, 1000);

    private UnwrappedMilestones() {
    }

    static Integer highestReached(long voteCount) {
        return VALUES.reversed().stream()
                .filter(candidate -> voteCount >= candidate)
                .findFirst()
                .orElse(null);
    }
}
