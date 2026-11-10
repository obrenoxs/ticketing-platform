package dev.obrenoxs.ticketing.platform.internal;

import java.time.Duration;

record RateLimitDecision(boolean allowed, Duration retryAfter) {

    static RateLimitDecision permit() {
        return new RateLimitDecision(true, Duration.ZERO);
    }

    static RateLimitDecision deny(Duration retryAfter) {
        return new RateLimitDecision(false, retryAfter);
    }
}
