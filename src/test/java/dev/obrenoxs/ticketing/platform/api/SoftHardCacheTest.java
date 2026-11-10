package dev.obrenoxs.ticketing.platform.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class SoftHardCacheTest {

    private static final Duration SOFT = Duration.ofSeconds(1);
    private static final Duration HARD = Duration.ofSeconds(5);

    private final AtomicLong nanos = new AtomicLong();
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final AtomicInteger loads = new AtomicInteger();

    private SoftHardCache<String, String> cache(Function<String, String> loader) {
        return new SoftHardCache<>(SOFT, HARD, 100, loader, nanos::get, pending::add);
    }

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    private void runPending() {
        Runnable task;
        while ((task = pending.poll()) != null) task.run();
    }

    @Test
    void shouldServeFromCacheWithinSoftTtl() {
        var cache = cache(k -> "v" + loads.incrementAndGet());
        assertThat(cache.get("a")).isEqualTo("v1");
        advance(Duration.ofMillis(500));
        assertThat(cache.get("a")).isEqualTo("v1");
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void shouldReturnStaleValueAndRefreshInBackgroundAfterSoftTtl() {
        var cache = cache(k -> "v" + loads.incrementAndGet());
        cache.get("a");
        advance(Duration.ofSeconds(2));

        assertThat(cache.get("a")).isEqualTo("v1");   // antigo, sem esperar
        runPending();                                  // recarga em segundo plano
        assertThat(cache.get("a")).isEqualTo("v2");   // já atualizado
    }

    @Test
    void shouldLoadSynchronouslyAfterHardTtl() {
        var cache = cache(k -> "v" + loads.incrementAndGet());
        cache.get("a");
        advance(Duration.ofSeconds(6));

        assertThat(cache.get("a")).isEqualTo("v2");   // sem precisar rodar a fila
    }

    @Test
    void shouldKeepServingOldValueWhenRefreshFails() {
        var cache = cache(k -> {
            if (loads.incrementAndGet() > 1) throw new IllegalStateException("db down");
            return "v1";
        });
        cache.get("a");
        advance(Duration.ofSeconds(2));

        assertThat(cache.get("a")).isEqualTo("v1");
        runPending();
        assertThat(cache.get("a")).isEqualTo("v1");   // recarga falhou, mantém o antigo
    }

    @Test
    void shouldLoadOnlyOnceWhenManyThreadsMissTheSameKey() throws Exception {
        var cache = new SoftHardCache<String, String>(SOFT, HARD, 100, k -> {
            loads.incrementAndGet();
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "v";
        });
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) futures.add(pool.submit(() -> cache.get("a")));
        for (Future<String> f : futures) f.get();
        pool.shutdown();

        assertThat(loads.get()).isEqualTo(1);
    }
}
