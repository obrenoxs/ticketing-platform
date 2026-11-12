package dev.obrenoxs.ticketing.auth.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(@NotBlank @Size(max = 200) String refreshToken) {}