package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import dev.obrenoxs.ticketing.platform.api.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    private final AccountRepository accounts;
    private final EmailVerificationTokenRepository tokens;
    private final PasswordHasher passwordHasher;
    private final CpfCipher cpfCipher;
    private final CpfBlindIndex cpfIndex;
    private final IdGenerator ids;
    private final DatabaseClock clock;
    private final EmailVerificationNotifier notifier;
    private final TransactionTemplate tx;
    private final AuthProperties properties;

    SignupService(AccountRepository accounts, EmailVerificationTokenRepository tokens,
                  PasswordHasher passwordHasher, CpfCipher cpfCipher, CpfBlindIndex cpfIndex,
                  IdGenerator ids, DatabaseClock clock, EmailVerificationNotifier notifier,
                  TransactionTemplate tx, AuthProperties properties) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordHasher = passwordHasher;
        this.cpfCipher = cpfCipher;
        this.cpfIndex = cpfIndex;
        this.ids = ids;
        this.clock = clock;
        this.notifier = notifier;
        this.tx = tx;
        this.properties = properties;
    }

    public void signup(String name, String rawEmail, String rawPassword, String rawCpf) {
        Cpf cpf = Cpf.of(rawCpf);
        String email = Account.normalizeEmail(rawEmail);

        String passwordHash = passwordHasher.hash(rawPassword);
        byte[] cpfHmac = cpfIndex.hash(cpf);

        if (accounts.existsByEmail(email) || accounts.existsByCpfHmac(cpfHmac)) {
            log.info("Signup ignored: email or CPF already registered.");
            return;
        }

        EncryptedCpf encryptedCpf = cpfCipher.encrypt(cpf);
        String token = OpaqueTokens.generate();
        Instant now = clock.now();

        Account account = Account.register(ids.next(), email, name, passwordHash,
                encryptedCpf.bytes(), encryptedCpf.keyVersion(), cpfHmac, now);
        EmailVerificationToken verification = new EmailVerificationToken(
                ids.next(), account.getId(), OpaqueTokens.hash(token),
                now.plus(properties.emailVerificationTtl()), now);

        try {
            tx.executeWithoutResult(status -> {
                accounts.saveAndFlush(account);
                tokens.saveAndFlush(verification);
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Signup ignored: concurrent registration of the same email or CPF.");
            return;
        }

        notifier.send(email, account.getName(),
                properties.publicBaseUrl() + "/auth/verify-email?token=" + token);
    }
}
