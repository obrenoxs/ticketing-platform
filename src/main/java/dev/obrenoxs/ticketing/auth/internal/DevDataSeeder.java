package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@EnableConfigurationProperties(SeedProperties.class)
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final SeedProperties seed;
    private final AdminBootstrapProperties adminProps;
    private final AccountRepository accounts;
    private final OrganizerProfileRepository profiles;
    private final PasswordHasher passwordHasher;
    private final CpfCipher cpfCipher;
    private final CpfBlindIndex cpfIndex;
    private final IdGenerator ids;
    private final DatabaseClock clock;

    DevDataSeeder(SeedProperties seed, AdminBootstrapProperties adminProps,
                  AccountRepository accounts, OrganizerProfileRepository profiles,
                  PasswordHasher passwordHasher, CpfCipher cpfCipher, CpfBlindIndex cpfIndex,
                  IdGenerator ids, DatabaseClock clock) {
        this.seed = seed;
        this.adminProps = adminProps;
        this.accounts = accounts;
        this.profiles = profiles;
        this.passwordHasher = passwordHasher;
        this.cpfCipher = cpfCipher;
        this.cpfIndex = cpfIndex;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seed.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.seed.password is required (at least " + MIN_PASSWORD_LENGTH + " characters).");
        }

        createAccount("buyer@example.com", "Seed Buyer", "12345678909", Role.BUYER);

        createAccount("organizer@example.com", "Seed Organizer", "98765432100", Role.ORGANIZER)
                .ifPresent(id -> {
                    profiles.saveAndFlush(OrganizerProfile.request(id, "Seed Productions", clock.now()));
                    approve(id);
                });

        createAccount("pending@example.com", "Seed Pending", "52998224725", Role.ORGANIZER)
                .ifPresent(id -> profiles.saveAndFlush(
                        OrganizerProfile.request(id, "Pending Productions", clock.now())));

        log.info("Dev seed finished.");
    }

    private Optional<UUID> createAccount(String rawEmail, String name, String rawCpf, Role role) {
        Cpf cpf = Cpf.of(rawCpf);
        String email = Account.normalizeEmail(rawEmail);
        byte[] cpfHmac = cpfIndex.hash(cpf);

        if (accounts.existsByEmail(email) || accounts.existsByCpfHmac(cpfHmac)) {
            log.info("Seed skipped, already present: {}", email);
            return Optional.empty();
        }

        EncryptedCpf encryptedCpf = cpfCipher.encrypt(cpf);
        Instant now = clock.now();
        Account account = Account.register(ids.next(), email, name,
                passwordHasher.hash(seed.password()),
                encryptedCpf.bytes(), encryptedCpf.keyVersion(), cpfHmac, now, role);
        account.activate(now);
        accounts.saveAndFlush(account);
        log.info("Seed account created: {} ({})", email, role);
        return Optional.of(account.getId());
    }

    private void approve(UUID organizerId) {
        Optional<UUID> admin = adminProps.email().isBlank()
                ? Optional.empty()
                : accounts.findByEmail(Account.normalizeEmail(adminProps.email())).map(Account::getId);

        admin.ifPresentOrElse(
                adminId -> profiles.approveIfPending(organizerId, adminId, clock.now()),
                () -> log.warn("Seed organizer left PENDING: configure the initial admin to approve it."));
    }
}
