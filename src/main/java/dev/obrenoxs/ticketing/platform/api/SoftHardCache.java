package dev.obrenoxs.ticketing.platform.api;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class SoftHardCache<K, V> {

    private final LoadingCache<K, V> cache;

    public SoftHardCache(Duration softTtl, Duration hardTtl, long maximumSize, Function<K, V> loader) {
        this(softTtl, hardTtl, maximumSize, loader, Ticker.systemTicker(), null);
    }

    SoftHardCache(Duration softTtl, Duration hardTtl, long maximumSize,
                  Function<K, V> loader, Ticker ticker, Executor refreshExecutor) {
        if (softTtl.isZero() || softTtl.isNegative() || softTtl.compareTo(hardTtl) >= 0) {
            throw new IllegalArgumentException("softTtl must be positive and smaller than hardTtl");
        }
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .refreshAfterWrite(softTtl)
                .expireAfterWrite(hardTtl)
                .recordStats()
                .ticker(ticker);
        if (refreshExecutor != null) {
            builder.executor(refreshExecutor);
        }
        this.cache = builder.build(loader::apply);
    }

    public V get(K key) {
        return cache.get(key);
    }

    public void invalidate(K key) {
        cache.invalidate(key);
    }

    public CacheStats stats() {
        return cache.stats();
    }
}