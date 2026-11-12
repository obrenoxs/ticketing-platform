package dev.obrenoxs.ticketing.auth.domain;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRegistrationRoleTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private static Account register(Role role) {
        return Account.register(UUID.randomUUID(), "ana@example.com", "Ana", "hash",
                new byte[] {1}, 1, new byte[] {2}, NOW, role);
    }

    @Test
    void shouldCreateOrganizerAccount() {
        assertThat(register(Role.ORGANIZER).getRole()).isEqualTo(Role.ORGANIZER);
    }

    @Test
    void shouldDefaultToBuyerWhenNoRoleIsGiven() {
        Account account = Account.register(UUID.randomUUID(), "ana@example.com", "Ana", "hash",
                new byte[] {1}, 1, new byte[] {2}, NOW);

        assertThat(account.getRole()).isEqualTo(Role.BUYER);
    }

    @Test
    void shouldNeverCreateAdminThroughRegistration() {
        assertThatThrownBy(() -> register(Role.ADMIN)).isInstanceOf(IllegalArgumentException.class);
    }
}
