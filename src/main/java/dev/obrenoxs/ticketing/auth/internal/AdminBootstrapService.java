package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;

    public enum Outcome { DISABLED, CREATED, ALREADY_EXISTS }

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final CpfCipher cpfCipher;
    private final CpfBlindIndex cpfIndex;
    private final IdGenerator ids;
    private final DatabaseClock clock;

    public AdminBootstrapService(AccountRepository accounts, PasswordHasher passwordHasher,
                                 CpfCipher cpfCipher, CpfBlindIndex cpfIndex,
                                 IdGenerator ids, DatabaseClock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.cpfCipher = cpfCipher;
        this.cpfIndex = cpfIndex;
        this.ids = ids;
        this.clock = clock;
    }

    public Outcome bootstrap(AdminBootstrapProperties props) {
        if (!props.configured()) {
            return Outcome.DISABLED;
        }
        requireStrongPassword(props.password());

        Cpf cpf = Cpf.of(props.cpf());
        String email = Account.normalizeEmail(props.email());
        byte[] cpfHmac = cpfIndex.hash(cpf);

        if (accounts.existsByEmail(email) || accounts.existsByCpfHmac(cpfHmac)) {
            log.warn("Initial admin not created: email or CPF already registered. Existing account left untouched.");
            return Outcome.ALREADY_EXISTS;
        }

        EncryptedCpf encryptedCpf = cpfCipher.encrypt(cpf);
        Account admin = Account.createAdmin(ids.next(), email, props.name(),
                passwordHasher.hash(props.password()),
                encryptedCpf.bytes(), encryptedCpf.keyVersion(), cpfHmac, clock.now());
        try {
            accounts.saveAndFlush(admin);
        } catch (DataIntegrityViolationException e) {
            log.info("Initial admin already created by another instance.");
            return Outcome.ALREADY_EXISTS;
        }

        log.info("Initial ADMIN account created for {}.", email);
        return Outcome.CREATED;
    }

    private static void requireStrongPassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("app.bootstrap.admin.password must have at least "
                    + MIN_PASSWORD_LENGTH + " characters and at most " + MAX_PASSWORD_BYTES + " bytes.");
        }
    }
}