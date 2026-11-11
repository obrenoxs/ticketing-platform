package dev.obrenoxs.ticketing.auth.internal;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {}