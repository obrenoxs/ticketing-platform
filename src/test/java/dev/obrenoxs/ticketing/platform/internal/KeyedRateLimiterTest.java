package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.platform.api.AppProperties;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class KeyedRateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);
    private final KeyedRateLimiter limiter = new KeyedRateLimiter(
            new AppProperties.RateLimit(true, 60, 5, 10, 20, 10, 5, 5), now::get);

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

    @Test
    void shouldLimitSignupPerHour() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.SIGNUP, "ip:1").allowed()).isTrue();
        }
        RateLimitDecision sixth = limiter.tryAcquire(RateLimitPolicy.SIGNUP, "ip:1");
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfter()).isBetween(Duration.ofSeconds(719), Duration.ofSeconds(721));

        now.addAndGet(Duration.ofSeconds(721).toNanos());
        assertThat(limiter.tryAcquire(RateLimitPolicy.SIGNUP, "ip:1").allowed()).isTrue();
    }

    @Test
    void shouldLimitLoginPerMinute() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, "ip:1").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(RateLimitPolicy.LOGIN, "ip:1").allowed()).isFalse();
    }

    @Test
    void shouldBlockOnlyAfterRecordedFailuresAndNeverConsumeOnCheck() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "a@x.com")).isZero();
        }
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(RateLimitPolicy.LOGIN_FAILURE, "a@x.com");
        }
        assertThat(limiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "a@x.com")).isPositive();
        assertThat(limiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "b@x.com")).isZero();

        now.addAndGet(13_000_000_000L); // uma ficha a cada 12 s
        assertThat(limiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "a@x.com")).isZero();
    }

    @Test
    void shouldNeverBlockWhenDisabled() {
        KeyedRateLimiter disabled = new KeyedRateLimiter(
                new AppProperties.RateLimit(false, 60, 5, 10, 20, 10, 5, 5), now::get);
        for (int i = 0; i < 20; i++) {
            disabled.recordFailure(RateLimitPolicy.LOGIN_FAILURE, "a@x.com");
        }
        assertThat(disabled.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "a@x.com")).isZero();
    }
}
