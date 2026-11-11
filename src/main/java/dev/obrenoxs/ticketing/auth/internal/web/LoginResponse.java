package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.IssuedAccessToken;
import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {

    static LoginResponse bearer(IssuedAccessToken token) {
        return new LoginResponse(token.value(), "Bearer", token.expiresAt());
    }
}