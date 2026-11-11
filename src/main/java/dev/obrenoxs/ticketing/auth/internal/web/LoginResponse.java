package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.domain.Role;

import java.util.UUID;

public record LoginResponse(UUID accountId, Role role) {}
