package dev.obrenoxs.ticketing.auth.repository;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.*;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRepositoriesTest extends AbstractIntegrationTest {

    @Autowired
    AccountRepository accounts;

    @Autowired
    EmailVerificationTokenRepository tokens;

    @Autowired
    OrganizerProfileRepository organizers;

    @Autowired
    RefreshTokenRepository refreshTokens;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Account newAccount(String email, byte[] cpfHmac) {
        return Account.register(UUID.randomUUID(), email, "Test", "hash",
                randomBytes(40), 1, cpfHmac, now());
    }

    private Account savedAccount() {
        return accounts.saveAndFlush(
                newAccount(UUID.randomUUID() + "@example.com", randomBytes(32)));
    }

    @Test
    void shouldPersistAndReloadAccount() {
        byte[] hmac = randomBytes(32);
        Account saved = accounts.saveAndFlush(newAccount("round@example.com".replace("round", UUID.randomUUID().toString()), hmac));

        Account loaded = accounts.findByEmail(saved.getEmail()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getRole()).isEqualTo(Role.BUYER);
        assertThat(loaded.getStatus()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(loaded.getCpfHmac()).isEqualTo(hmac);
        assertThat(loaded.getCpfEncrypted()).isEqualTo(saved.getCpfEncrypted());
        assertThat(loaded.getCpfKeyVersion()).isEqualTo(1);
        assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(loaded.isNew()).isFalse();
    }

    @Test
    void shouldFindByEmailAndCpfHmac() {
        byte[] hmac = randomBytes(32);
        String email = UUID.randomUUID() + "@example.com";
        accounts.saveAndFlush(newAccount(email, hmac));

        assertThat(accounts.existsByEmail(email)).isTrue();
        assertThat(accounts.existsByCpfHmac(hmac)).isTrue();
        assertThat(accounts.existsByEmail("nobody@example.com")).isFalse();
        assertThat(accounts.existsByCpfHmac(randomBytes(32))).isFalse();
    }

    @Test
    void shouldRejectDuplicateEmailAtDatabaseLevel() {
        String email = UUID.randomUUID() + "@example.com";
        accounts.saveAndFlush(newAccount(email, randomBytes(32)));

        assertThatThrownBy(() -> accounts.saveAndFlush(newAccount(email, randomBytes(32))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateCpfAtDatabaseLevel() {
        byte[] hmac = randomBytes(32);
        accounts.saveAndFlush(newAccount(UUID.randomUUID() + "@example.com", hmac));

        assertThatThrownBy(() ->
                accounts.saveAndFlush(newAccount(UUID.randomUUID() + "@example.com", hmac)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldPersistActivation() {
        Account saved = savedAccount();
        saved.activate(now());
        accounts.saveAndFlush(saved);

        assertThat(accounts.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void shouldFindVerificationTokenByHash() {
        Account account = savedAccount();
        byte[] hash = randomBytes(32);
        tokens.saveAndFlush(new EmailVerificationToken(
                UUID.randomUUID(), account.getId(), hash, now().plusSeconds(3600), now()));

        EmailVerificationToken found = tokens.findByTokenHash(hash).orElseThrow();
        assertThat(found.getAccountId()).isEqualTo(account.getId());
        assertThat(found.isUsable(now())).isTrue();
    }

    @Test
    void shouldPersistOrganizerDecision() {
        Account organizer = savedAccount();
        Account admin = savedAccount();
        OrganizerProfile profile = OrganizerProfile.request(organizer.getId(), "Produtora", now());
        organizers.saveAndFlush(profile);

        profile.approve(admin.getId(), now());
        organizers.saveAndFlush(profile);

        OrganizerProfile loaded = organizers.findById(organizer.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrganizerStatus.APPROVED);
        assertThat(loaded.getDecidedBy()).isEqualTo(admin.getId());
    }

    @Test
    void shouldFindRefreshTokenByHash() {
        Account account = savedAccount();
        byte[] hash = randomBytes(32);
        UUID family = UUID.randomUUID();
        refreshTokens.saveAndFlush(new RefreshToken(
                UUID.randomUUID(), account.getId(), family, hash, now().plusSeconds(86400), now()));

        RefreshToken found = refreshTokens.findByTokenHash(hash).orElseThrow();
        assertThat(found.getFamilyId()).isEqualTo(family);
        assertThat(found.getRevokedAt()).isNull();
    }
}
