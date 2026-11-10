package dev.obrenoxs.ticketing.platform.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.function.LongSupplier;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class KeyedRateLimiter {

    private static final long MAX_KEYS = 100_000;
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(2);

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
        String key = policy.name() + ":" + identifier;
        TokenBucket bucket = buckets.get(key, k -> newBucket(policy));
        if (bucket.tryAcquire()) {
            return RateLimitDecision.permit();
        }
        return RateLimitDecision.deny(bucket.timeUntilNextToken());
    }

    private TokenBucket newBucket(RateLimitPolicy policy) {
        int perMinute = perMinute(policy);
        return new TokenBucket(perMinute, perMinute / 60.0, nanoClock);
    }

    private int perMinute(RateLimitPolicy policy) {
        return switch (policy) {
            case READS -> limits.readsPerMinute();
            case RESERVE -> limits.reservesPerMinute();
            case PAYMENT -> limits.paymentsPerMinute();
            case NOTIFICATIONS -> limits.notificationsPerMinute();
        };
    }
}
