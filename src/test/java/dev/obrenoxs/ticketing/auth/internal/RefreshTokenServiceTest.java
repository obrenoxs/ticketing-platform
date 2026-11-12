package dev.obrenoxs.ticketing.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.RefreshToken;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.RefreshTokenRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuthProperties properties = mock(AuthProperties.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final DatabaseClock clock = mock(DatabaseClock.class);
    private final RefreshTokenService service =
            new RefreshTokenService(tokens, accounts, properties, ids, clock);

    @BeforeEach
    void setUp() {
        when(clock.now()).thenReturn(NOW);
        when(properties.refreshTokenTtl()).thenReturn(Duration.ofDays(15));
        when(ids.next()).thenAnswer(invocation -> UUID.randomUUID());
    }

    private static Account account(boolean active) {
        Account account = Account.register(UUID.randomUUID(), "ana@example.com", "Ana",
                "hash", new byte[] {1}, 1, new byte[] {2}, NOW);
        if (active) {
            account.activate(NOW);
        }
        return account;
    }

    private static RefreshToken storedToken(UUID accountId, UUID familyId, Instant usedAt) {
        RefreshToken token = mock(RefreshToken.class);
        when(token.getAccountId()).thenReturn(accountId);
        when(token.getFamilyId()).thenReturn(familyId);
        when(token.getUsedAt()).thenReturn(usedAt);
        return token;
    }

    private static void assertInvalidRefreshToken(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void shouldStoreOnlyTheHashWhenIssuingForLogin() {
        UUID accountId = UUID.randomUUID();

        IssuedRefreshToken issued = service.issueForLogin(accountId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(15)));
        assertThat(issued.expiresAt()).isEqualTo(saved.getExpiresAt());
        assertThat(saved.getTokenHash()).isEqualTo(OpaqueTokens.hash(issued.value()));
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.value().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldRotateIntoANewTokenOfTheSameFamily() {
        Account account = account(true);
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(account.getId(), family, null);
        when(tokens.markUsed(any(), eq(NOW))).thenReturn(1);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        RotationResult result = service.rotate("old-token");

        assertThat(result.account().accountId()).isEqualTo(account.getId());
        assertThat(result.account().role()).isEqualTo(Role.BUYER);
        assertThat(result.refreshToken().value()).isNotEqualTo("old-token");
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().getFamilyId()).isEqualTo(family);
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void shouldRevokeFamilyAndRefuseWhenAccountIsNoLongerActive() {
        Account pending = account(false);
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(pending.getId(), family, null);
        when(tokens.markUsed(any(), eq(NOW))).thenReturn(1);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(accounts.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertInvalidRefreshToken(() -> service.rotate("old-token"));

        verify(tokens).revokeFamily(family, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void shouldRevokeWholeFamilyWhenAnAlreadyUsedTokenIsPresentedAgain() {
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(UUID.randomUUID(), family, NOW.minusSeconds(60));
        when(tokens.markUsed(any(), eq(NOW))).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertInvalidRefreshToken(() -> service.rotate("stolen-token"));

        verify(tokens).revokeFamily(family, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void shouldJustRefuseUnknownTokenWithoutRevokingAnything() {
        when(tokens.markUsed(any(), eq(NOW))).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertInvalidRefreshToken(() -> service.rotate("made-up"));

        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void shouldJustRefuseExpiredOrRevokedTokenWithoutRevokingAnything() {
        RefreshToken stored = storedToken(UUID.randomUUID(), UUID.randomUUID(), null);
        when(tokens.markUsed(any(), eq(NOW))).thenReturn(0);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(stored));

        assertInvalidRefreshToken(() -> service.rotate("expired-or-revoked"));

        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void shouldRevokeFamilyOnLogout() {
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(UUID.randomUUID(), family, null);
        when(tokens.findByTokenHash(any())).thenReturn(Optional.of(stored));

        service.logout("some-token");

        verify(tokens).revokeFamily(family, NOW);
    }

    @Test
    void shouldIgnoreLogoutOfUnknownToken() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatCode(() -> service.logout("made-up")).doesNotThrowAnyException();
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void rotateMustKeepItsRevocationEvenWhenItThrows() throws NoSuchMethodException {
        Method rotate = RefreshTokenService.class.getMethod("rotate", String.class);

        assertThat(rotate.getAnnotation(Transactional.class).noRollbackFor())
                .contains(BusinessException.class);
    }
}