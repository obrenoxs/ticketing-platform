package dev.obrenoxs.ticketing.platform.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;

@Component
class PostgresDatabaseClock implements DatabaseClock {

    private final JdbcClient jdbc;

    PostgresDatabaseClock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Instant now() {
        return jdbc.sql("SELECT now()")
                .query(OffsetDateTime.class)
                .single()
                .toInstant();
    }
}
