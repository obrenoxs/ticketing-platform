package dev.obrenoxs.ticketing.platform.internal;

import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7IdGeneratorTest {

    private final IdGenerator generator = new UuidV7IdGenerator();

    @Test
    void shouldGenerateVersion7() {
        assertThat(generator.next().version()).isEqualTo(7);
    }

    @Test
    void shouldGenerateUniqueIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(generator.next());
        }
        assertThat(ids).hasSize(100_000);
    }

    @Test
    void shouldGenerateIdsInTimeOrder() throws InterruptedException {
        UUID first = generator.next();
        Thread.sleep(2);
        UUID second = generator.next();
        assertThat(first.toString()).isLessThan(second.toString());
    }
}
