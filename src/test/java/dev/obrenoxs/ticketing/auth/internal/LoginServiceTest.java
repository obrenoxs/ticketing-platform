package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.platform.api.AttemptLimiter;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.RateLimitPolicy;
import dev.obrenoxs.ticketing.platform.api.TooManyRequestsException;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordHasher hasher = spy(new PasswordHasher(4));
    private final AttemptLimiter limiter = mock(AttemptLimiter.class);
    private final LoginService service = new LoginService(accounts, hasher, limiter);

    private Account account(boolean active) {
        Account account = Account.register(UUID.randomUUID(), "ana@example.com", "Ana",
                hasher.hash("password-123"), new byte[] {1}, 1, new byte[] {2}, NOW);
        if (active) {
            account.activate(NOW);
        }
        return account;
    }

    private static void assertInvalidCredentials(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @BeforeEach
    void notBlocked() {
        when(limiter.blockedFor(any(), any())).thenReturn(Duration.ZERO);
    }

    @Test
    void shouldAuthenticateActiveAccountWithCorrectPassword() {
        Account active = account(true);
        when(accounts.findByEmail("ana@example.com")).thenReturn(Optional.of(active));

        AuthenticatedAccount result = service.login("  Ana@Example.com ", "password-123");

        assertThat(result.accountId()).isEqualTo(active.getId());
        assertThat(result.role()).isEqualTo(Role.BUYER);
        verify(limiter, never()).recordFailure(any(), any());
    }

    @Test
    void shouldRejectWrongPasswordAndRecordFailure() {
        Account active = account(true);
        when(accounts.findByEmail("ana@example.com")).thenReturn(Optional.of(active));

        assertInvalidCredentials(() -> service.login("ana@example.com", "wrong-password"));
        verify(limiter).recordFailure(RateLimitPolicy.LOGIN_FAILURE, "ana@example.com");
    }

    @Test
    void shouldRejectUnknownEmailWithSameErrorAndStillSpendHashTime() {
        when(accounts.findByEmail(any())).thenReturn(Optional.empty());

        assertInvalidCredentials(() -> service.login("nobody@example.com", "password-123"));
        verify(hasher).simulateVerification("password-123");
        verify(limiter).recordFailure(RateLimitPolicy.LOGIN_FAILURE, "nobody@example.com");
    }

    @Test
    void shouldRejectCorrectPasswordWhenEmailIsNotConfirmedYet() {
        Account pending = account(false);
        when(accounts.findByEmail("ana@example.com")).thenReturn(Optional.of(pending));

        assertInvalidCredentials(() -> service.login("ana@example.com", "password-123"));
        verify(limiter).recordFailure(RateLimitPolicy.LOGIN_FAILURE, "ana@example.com");
    }

    @Test
    void shouldRefuseBeforeTouchingDatabaseOrHashingWhenBlocked() {
        when(limiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, "ana@example.com"))
                .thenReturn(Duration.ofSeconds(8));

        assertThatThrownBy(() -> service.login("ana@example.com", "password-123"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo(Duration.ofSeconds(8)));

        verifyNoInteractions(accounts);
        verify(hasher, never()).matches(any(), any());
        verify(hasher, never()).simulateVerification(any());
    }
}
