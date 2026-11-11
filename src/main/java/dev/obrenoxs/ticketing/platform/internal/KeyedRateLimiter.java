package dev.obrenoxs.ticketing.platform.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.LongSupplier;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import dev.obrenoxs.ticketing.platform.api.AttemptLimiter;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class KeyedRateLimiter implements AttemptLimiter {

    private static final long MAX_KEYS = 100_000;
    private static final Duration IDLE_EVICTION = Duration.ofHours(1);

    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(IDLE_EVICTION)
            .build();

    private final AppProperties.RateLimit limits;
    private final LongSupplier nanoClock;

    @Autowired
    KeyedRateLimiter(AppProperties properties) {
        this(properties.rateLimit(), System::nanoTime);
    }

    KeyedRateLimiter(AppProperties.RateLimit limits, LongSupplier nanoClock) {
        this.limits = limits;
        this.nanoClock = nanoClock;
    }

    RateLimitDecision tryAcquire(RateLimitPolicy policy, String identifier) {
        TokenBucket bucket = bucket(policy, identifier);
        if (bucket.tryAcquire()) {
            return RateLimitDecision.permit();
        }
        return RateLimitDecision.deny(bucket.timeUntilNextToken());
    }

    @Override
    public Duration blockedFor(RateLimitPolicy policy, String key) {
        if (!limits.enabled()) {
            return Duration.ZERO;
        }
        return bucket(policy, key).timeUntilNextToken();
    }

    @Override
    public void recordFailure(RateLimitPolicy policy, String key) {
        if (limits.enabled()) {
            bucket(policy, key).tryAcquire();
        }
    }

    private TokenBucket bucket(RateLimitPolicy policy, String identifier) {
        return buckets.get(policy.name() + ":" + identifier, k -> newBucket(policy));
    }

    private TokenBucket newBucket(RateLimitPolicy policy) {
        int capacity = capacity(policy);
        long periodSeconds = period(policy).toSeconds();
        return new TokenBucket(capacity, capacity / (double) periodSeconds, nanoClock);
    }

    private Duration period(RateLimitPolicy policy) {
        return policy == RateLimitPolicy.SIGNUP ? Duration.ofHours(1) : Duration.ofMinutes(1);
    }

    private int capacity(RateLimitPolicy policy) {
        return switch (policy) {
            case READS -> limits.readsPerMinute();
            case RESERVE -> limits.reservesPerMinute();
            case PAYMENT -> limits.paymentsPerMinute();
            case NOTIFICATIONS -> limits.notificationsPerMinute();
            case LOGIN -> limits.loginPerMinute();
            case LOGIN_FAILURE -> limits.loginFailuresPerMinute();
            case SIGNUP -> limits.signupPerHour();
        };
    }
}
