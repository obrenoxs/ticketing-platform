package dev.obrenoxs.ticketing.platform.api;

import java.time.Duration;

public interface AttemptLimiter {

    Duration blockedFor(RateLimitPolicy policy, String key);

    void recordFailure(RateLimitPolicy policy, String key);
}
