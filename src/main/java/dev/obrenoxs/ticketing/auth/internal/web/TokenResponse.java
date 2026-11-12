package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.IssuedAccessToken;
import dev.obrenoxs.ticketing.auth.internal.IssuedRefreshToken;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt,
                            String refreshToken, Instant refreshExpiresAt) {

    static TokenResponse of(IssuedAccessToken access, IssuedRefreshToken refresh) {
        return new TokenResponse(access.value(), "Bearer", access.expiresAt(),
                refresh.value(), refresh.expiresAt());
    }
}