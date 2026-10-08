package com.yoursay.platform.mobiletelemetry.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousUploadBudgetTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));

    @Test
    void refusesOnceTheMinuteIsSpentAndRefillsWhenTheNextMinuteStarts() {
        AnonymousUploadBudget budget = new AnonymousUploadBudget(2, clock);

        assertTrue(budget.tryAcquire());
        assertTrue(budget.tryAcquire());
        clock.advance(Duration.ofSeconds(59));
        assertFalse(budget.tryAcquire());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(budget.tryAcquire());
    }

    @Test
    void zeroBudgetRefusesEveryAnonymousBatch() {
        AnonymousUploadBudget budget = new AnonymousUploadBudget(0, clock);

        assertFalse(budget.tryAcquire());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
