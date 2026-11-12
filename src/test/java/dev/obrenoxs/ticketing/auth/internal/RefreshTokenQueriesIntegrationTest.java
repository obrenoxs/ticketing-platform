package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.RefreshToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenQueriesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AccountRepository accounts;

    @Autowired
    RefreshTokenRepository tokens;

    @Autowired
    PasswordHasher hasher;

    private UUID savedAccountId() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Account account = Account.register(UUID.randomUUID(), UUID.randomUUID() + "@example.com", "Ana",
                hasher.hash("password-123"), new byte[] {1}, 1,
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), now);
        return accounts.saveAndFlush(account).getId();
    }

    private RefreshToken savedToken(UUID accountId, UUID familyId, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return tokens.saveAndFlush(new RefreshToken(UUID.randomUUID(), accountId, familyId,
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), expiresAt, now));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    @Transactional
    void shouldConsumeValidTokenOnlyOnce() {
        RefreshToken token = savedToken(savedAccountId(), UUID.randomUUID(), now().plusSeconds(3600));

        assertThat(tokens.markUsed(token.getTokenHash(), now())).isEqualTo(1);
        assertThat(tokens.markUsed(token.getTokenHash(), now())).isEqualTo(0);
        assertThat(tokens.findById(token.getId()).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    @Transactional
    void shouldNotConsumeExpiredToken() {
        RefreshToken token = savedToken(savedAccountId(), UUID.randomUUID(), now().minusSeconds(1));

        assertThat(tokens.markUsed(token.getTokenHash(), now())).isEqualTo(0);
    }

    @Test
    @Transactional
    void shouldNotConsumeRevokedToken() {
        UUID family = UUID.randomUUID();
        RefreshToken token = savedToken(savedAccountId(), family, now().plusSeconds(3600));

        tokens.revokeFamily(family, now());

        assertThat(tokens.markUsed(token.getTokenHash(), now())).isEqualTo(0);
    }

    @Test
    @Transactional
    void shouldRevokeTheWholeFamilyAndNothingElse() {
        UUID accountId = savedAccountId();
        UUID familyA = UUID.randomUUID();
        UUID familyB = UUID.randomUUID();
        RefreshToken a1 = savedToken(accountId, familyA, now().plusSeconds(3600));
        RefreshToken a2 = savedToken(accountId, familyA, now().plusSeconds(3600));
        RefreshToken b1 = savedToken(accountId, familyB, now().plusSeconds(3600));
        tokens.markUsed(a1.getTokenHash(), now());

        int revoked = tokens.revokeFamily(familyA, now());

        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findById(a1.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(tokens.findById(a2.getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(tokens.findById(b1.getId()).orElseThrow().getRevokedAt()).isNull();
    }
}
