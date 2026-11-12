package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.RefreshToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.RefreshTokenRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokens;
    private final AccountRepository accounts;
    private final AuthProperties properties;
    private final IdGenerator ids;
    private final DatabaseClock clock;

    RefreshTokenService(RefreshTokenRepository tokens, AccountRepository accounts,
                        AuthProperties properties, IdGenerator ids, DatabaseClock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.properties = properties;
        this.ids = ids;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issueForLogin(UUID accountId) {
        return create(accountId, ids.next(), clock.now());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RotationResult rotate(String rawToken) {
        byte[] hash = OpaqueTokens.hash(rawToken);
        Instant now = clock.now();

        if (tokens.markUsed(hash, now) == 1) {
            RefreshToken used = tokens.findByTokenHash(hash).orElseThrow();
            Account account = accounts.findById(used.getAccountId()).orElseThrow();
            if (account.getStatus() != AccountStatus.ACTIVE) {
                tokens.revokeFamily(used.getFamilyId(), now);
                throw invalidToken();
            }
            IssuedRefreshToken next = create(account.getId(), used.getFamilyId(), now);
            return new RotationResult(
                    new AuthenticatedAccount(account.getId(), account.getRole()), next);
        }

        tokens.findByTokenHash(hash)
                .filter(token -> token.getUsedAt() != null)
                .ifPresent(reused -> tokens.revokeFamily(reused.getFamilyId(), now));
        throw invalidToken();
    }

    @Transactional
    public void logout(String rawToken) {
        tokens.findByTokenHash(OpaqueTokens.hash(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.now()));
    }

    private IssuedRefreshToken create(UUID accountId, UUID familyId, Instant now) {
        String raw = OpaqueTokens.generate();
        Instant expiresAt = now.plus(properties.refreshTokenTtl());
        tokens.save(new RefreshToken(
                ids.next(), accountId, familyId, OpaqueTokens.hash(raw), expiresAt, now));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    private static BusinessException invalidToken() {
        return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN,
                "Invalid or expired refresh token.");
    }
}