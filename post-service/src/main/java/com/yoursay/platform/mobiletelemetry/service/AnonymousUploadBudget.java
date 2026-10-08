package com.yoursay.platform.mobiletelemetry.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A service-wide cap on telemetry batches from callers who have not signed in, per fixed one-minute
 * window. The relay must accept pre-sign-in diagnostics (a failed Google sign-in has no token), which
 * makes it a public endpoint; this keeps a flood of anonymous uploads from filling Grafana Cloud's log
 * quota. Signed-in uploads are never counted against it.
 *
 * <p>It is global rather than per client because the dev server sees every request through Cloudflare
 * Tunnel, so there is no trustworthy client address to key on without extra proxy configuration.
 */
final class AnonymousUploadBudget {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limitPerMinute;
    private final Clock clock;
    private Instant windowStart = Instant.MIN;
    private int usedInWindow;

    AnonymousUploadBudget(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (!now.isBefore(windowStart.plus(WINDOW))) {
            windowStart = now;
            usedInWindow = 0;
        }
        if (usedInWindow >= limitPerMinute) {
            return false;
        }
        usedInWindow++;
        return true;
    }
}
