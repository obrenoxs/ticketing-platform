package dev.obrenoxs.ticketing.auth.internal;

import java.time.Instant;

public record IssuedRefreshToken(String value, Instant expiresAt) {}
