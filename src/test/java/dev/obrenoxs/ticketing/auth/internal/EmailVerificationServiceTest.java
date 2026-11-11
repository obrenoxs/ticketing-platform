package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private final EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final EmailVerificationService service =
            new EmailVerificationService(tokens, accounts, () -> NOW);

    private static byte[] hashOf(String token) {
        return OpaqueTokens.hash(token);
    }

    private static EmailVerificationToken tokenFor(UUID accountId, Instant usedAt) {
        EmailVerificationToken token = mock(EmailVerificationToken.class);
        when(token.getAccountId()).thenReturn(accountId);
        when(token.getUsedAt()).thenReturn(usedAt);
        return token;
    }

    private static Account accountWithStatus(AccountStatus status) {
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(status);
        return account;
    }

    private static void assertInvalid(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_VERIFICATION_TOKEN);
    }

    @Test
    void shouldConsumeTokenAndActivateAccount() {
        EmailVerificationToken token = tokenFor(ACCOUNT_ID, NOW);
        when(tokens.markUsed(hashOf("abc"), NOW)).thenReturn(1);
        when(tokens.findByTokenHash(hashOf("abc"))).thenReturn(Optional.of(token));
        when(accounts.activate(ACCOUNT_ID, NOW)).thenReturn(1);

        service.verify("abc");

        verify(accounts).activate(ACCOUNT_ID, NOW);
    }

    @Test
    void shouldRejectUnknownToken() {
        when(tokens.markUsed(any(), any())).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertInvalid(() -> service.verify("unknown"));
        verify(accounts, never()).activate(any(), any());
    }

    @Test
    void shouldRejectExpiredTokenThatWasNeverUsed() {
        EmailVerificationToken token = tokenFor(ACCOUNT_ID, null);
        when(tokens.markUsed(any(), any())).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertInvalid(() -> service.verify("expired"));
    }

    @Test
    void shouldAcceptRepeatedClickWhenAccountIsAlreadyActive() {
        EmailVerificationToken token = tokenFor(ACCOUNT_ID, NOW.minusSeconds(5));
        Account active = accountWithStatus(AccountStatus.ACTIVE);
        when(tokens.markUsed(any(), any())).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(active));

        service.verify("abc"); // não lança

        verify(accounts, never()).activate(any(), any());
    }

    @Test
    void shouldRejectUsedTokenWhenAccountIsNotActive() {
        EmailVerificationToken token = tokenFor(ACCOUNT_ID, NOW.minusSeconds(5));
        Account suspended = accountWithStatus(AccountStatus.SUSPENDED);
        when(tokens.markUsed(any(), any())).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(suspended));

        assertInvalid(() -> service.verify("abc"));
    }

    @Test
    void shouldRejectWhenAccountCannotBeActivated() {
        EmailVerificationToken token = tokenFor(ACCOUNT_ID, NOW);
        when(tokens.markUsed(any(), any())).thenReturn(1);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(accounts.activate(ACCOUNT_ID, NOW)).thenReturn(0);

        assertInvalid(() -> service.verify("abc"));
    }
}