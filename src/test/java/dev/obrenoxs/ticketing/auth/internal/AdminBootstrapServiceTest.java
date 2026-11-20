package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminBootstrapServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    private static final byte[] KEY = new byte[32];

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AdminBootstrapService service = new AdminBootstrapService(
            accounts, new PasswordHasher(4),
            new CpfCipher(Map.of(1, KEY), 1), new CpfBlindIndex(KEY),
            UUID::randomUUID, () -> NOW);

    private static AdminBootstrapProperties props(String password) {
        return new AdminBootstrapProperties("Admin@Example.com", "Admin", password, "52998224725");
    }

    @Test
    void shouldDoNothingWhenNotConfigured() {
        assertThat(service.bootstrap(new AdminBootstrapProperties("", "", "", "")))
                .isEqualTo(AdminBootstrapService.Outcome.DISABLED);
        verifyNoInteractions(accounts);
    }

    @Test
    void shouldCreateAnActiveAdminWithoutStoringSecretsInPlainText() {
        assertThat(service.bootstrap(props("a-strong-password"))).isEqualTo(AdminBootstrapService.Outcome.CREATED);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).saveAndFlush(saved.capture());
        Account admin = saved.getValue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");
        assertThat(admin.getPasswordHash()).isNotEqualTo("a-strong-password");
        assertThat(new String(admin.getCpfEncrypted(), StandardCharsets.ISO_8859_1)).doesNotContain("52998224725");
    }

    @Test
    void shouldLeaveAnExistingAccountUntouched() {
        when(accounts.existsByEmail("admin@example.com")).thenReturn(true);

        assertThat(service.bootstrap(props("a-strong-password")))
                .isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void shouldLeaveAnExistingCpfUntouched() {
        when(accounts.existsByCpfHmac(any())).thenReturn(true);

        assertThat(service.bootstrap(props("a-strong-password")))
                .isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void shouldRefuseToStartWithAWeakPassword() {
        assertThatThrownBy(() -> service.bootstrap(props("short-pass")))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void shouldTreatAConcurrentCreationAsAlreadyExisting() {
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.bootstrap(props("a-strong-password")))
                .isEqualTo(AdminBootstrapService.Outcome.ALREADY_EXISTS);
    }
}