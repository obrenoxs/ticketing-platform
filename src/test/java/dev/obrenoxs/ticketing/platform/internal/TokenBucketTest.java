package dev.obrenoxs.ticketing.platform.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenBucketTest {

    private final AtomicLong now = new AtomicLong(0);

    private TokenBucket bucket(long capacity, double perSecond) {
        return new TokenBucket(capacity, perSecond, now::get);
    }

    private static long seconds(long s) {
        return s * 1_000_000_000L;
    }

    @Test
    void shouldAllowUpToCapacityThenReject() {
        TokenBucket b = bucket(3, 1);
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
    }

    @Test
    void shouldRefillOverTime() {
        TokenBucket b = bucket(2, 1);
        b.tryAcquire();
        b.tryAcquire();
        assertThat(b.tryAcquire()).isFalse();
        now.addAndGet(seconds(1));
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
    }

    @Test
    void shouldNotExceedCapacityAfterLongIdle() {
        TokenBucket b = bucket(2, 1);
        now.addAndGet(seconds(3600));
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isTrue();
        assertThat(b.tryAcquire()).isFalse();
    }

    @Test
    void shouldReportTimeUntilNextToken() {
        TokenBucket b = bucket(1, 0.5); // 1 ficha a cada 2 s
        b.tryAcquire();
        assertThat(b.timeUntilNextToken()).isEqualTo(Duration.ofSeconds(2));
        now.addAndGet(seconds(1));
        assertThat(b.timeUntilNextToken()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void shouldNeverGrantMoreThanCapacityUnderConcurrency() throws Exception {
        TokenBucket b = bucket(100, 0.000001);
        int threads = 1000;
        AtomicInteger granted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                if (b.tryAcquire()) granted.incrementAndGet();
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get();
        pool.shutdown();
        assertThat(granted.get()).isEqualTo(100);
    }
}
