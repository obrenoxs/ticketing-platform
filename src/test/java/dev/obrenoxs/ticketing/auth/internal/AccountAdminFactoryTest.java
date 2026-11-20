package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAdminFactoryTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    @Test
    void shouldCreateAnActiveAdmin() {
        Account admin = Account.createAdmin(UUID.randomUUID(), " Admin@Example.com ", " Admin ", "hash",
                new byte[] {1}, 1, new byte[] {2}, NOW);

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    void signupMustStillRefuseAdmin() {
        assertThatThrownBy(() -> Account.register(UUID.randomUUID(), "a@example.com", "A", "hash",
                new byte[] {1}, 1, new byte[] {2}, NOW, Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
