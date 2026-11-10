package dev.obrenoxs.ticketing.platform.internal;

import java.time.Duration;
import java.util.function.LongSupplier;

final class TokenBucket {

    private final long capacity;
    private final double refillPerNano;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefill;

    TokenBucket(long capacity, double refillPerSecond, LongSupplier nanoClock) {
        if (capacity < 1 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity must be >= 1 and refill rate > 0");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefill = nanoClock.getAsLong();
    }

    synchronized boolean tryAcquire() {
        refill();
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
    }

    synchronized Duration timeUntilNextToken() {
        refill();
        if (tokens >= 1) {
            return Duration.ZERO;
        }
        double missing = 1 - tokens;
        return Duration.ofNanos((long) Math.ceil(missing / refillPerNano));
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefill;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefill = now;
        }
    }
}
