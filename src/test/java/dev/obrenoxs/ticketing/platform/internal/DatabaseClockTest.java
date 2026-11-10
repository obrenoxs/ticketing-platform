package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseClockTest extends AbstractIntegrationTest {

    @Autowired
    DatabaseClock clock;

    @Autowired
    TransactionTemplate tx;

    @Test
    void shouldReturnTimeCloseToApplicationTime() {
        Duration diff = Duration.between(clock.now(), Instant.now()).abs();
        assertThat(diff).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void shouldReturnSameValueInsideOneTransaction() {
        tx.executeWithoutResult(status -> {
            Instant first = clock.now();
            sleep(50);
            Instant second = clock.now();
            assertThat(second).isEqualTo(first);
        });
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
