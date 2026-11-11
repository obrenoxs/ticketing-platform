package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Role;

import java.util.UUID;

public record AuthenticatedAccount(UUID accountId, Role role) {}
