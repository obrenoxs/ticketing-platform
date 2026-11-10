package dev.obrenoxs.ticketing.platform.api;

import java.time.Instant;

public interface DatabaseClock {
    Instant now();
}