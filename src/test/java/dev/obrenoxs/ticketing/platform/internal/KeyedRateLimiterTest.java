package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedRateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);
    private final KeyedRateLimiter limiter = new KeyedRateLimiter(
            new AppProperties.RateLimit(true, 60, 5, 10, 20), now::get);

    @Test
    void shouldRejectAfterPolicyLimit() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1").allowed()).isTrue();
        }
        RateLimitDecision sixth = limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1");
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfter()).isPositive();
    }

    @Test
    void shouldKeepIdentifiersIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1");
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1").allowed()).isFalse();
        assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-2").allowed()).isTrue();
    }

    @Test
    void shouldKeepPoliciesIndependentForSameIdentifier() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1");
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1").allowed()).isFalse();
        assertThat(limiter.tryAcquire(RateLimitPolicy.READS, "acc-1").allowed()).isTrue();
    }

    @Test
    void shouldAllowAgainAfterRefill() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1");
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1").allowed()).isFalse();
        now.addAndGet(12_000_000_000L); // 12 s = 1 ficha (5 por minuto)
        assertThat(limiter.tryAcquire(RateLimitPolicy.RESERVE, "acc-1").allowed()).isTrue();
    }
}
