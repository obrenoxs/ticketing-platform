package dev.obrenoxs.ticketing.auth.internal;

public record RotationResult(AuthenticatedAccount account, IssuedRefreshToken refreshToken) {}
