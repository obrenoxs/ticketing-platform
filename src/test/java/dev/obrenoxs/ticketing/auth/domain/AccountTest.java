package dev.obrenoxs.ticketing.auth.domain;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private Account account(String email) {
        return Account.register(UUID.randomUUID(), email, " Ana ", "hash",
                new byte[] {1}, 1, new byte[] {2}, NOW);
    }

    @Test
    void shouldNormalizeEmailAndName() {
        Account a = account("  Ana@Example.COM ");
        assertThat(a.getEmail()).isEqualTo("ana@example.com");
        assertThat(a.getName()).isEqualTo("Ana");
    }

    @Test
    void shouldStartAsBuyerPendingEmail() {
        Account a = account("a@example.com");
        assertThat(a.getRole()).isEqualTo(Role.BUYER);
        assertThat(a.getStatus()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(a.isNew()).isTrue();
    }

    @Test
    void shouldActivateOnlyOnce() {
        Account a = account("a@example.com");
        a.activate(NOW.plusSeconds(60));
        assertThat(a.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(a.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> a.activate(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
