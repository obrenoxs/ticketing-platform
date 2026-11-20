package dev.obrenoxs.ticketing.auth.api;

import java.util.UUID;

public record CurrentAccount(UUID id, AccountRole role) {}
