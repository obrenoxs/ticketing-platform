package dev.obrenoxs.ticketing.platform.api;

import java.util.UUID;

public interface IdGenerator {
    UUID next();
}
