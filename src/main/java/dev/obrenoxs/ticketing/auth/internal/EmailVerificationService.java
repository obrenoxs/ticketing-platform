package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.EmailVerificationTokenRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokens;
    private final AccountRepository accounts;
    private final DatabaseClock clock;

    EmailVerificationService(EmailVerificationTokenRepository tokens,
                             AccountRepository accounts, DatabaseClock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public void verify(String rawToken) {
        byte[] hash = OpaqueTokens.hash(rawToken);
        Instant now = clock.now();

        if (tokens.markUsed(hash, now) == 1) {
            EmailVerificationToken token = tokens.findByTokenHash(hash).orElseThrow();
            if (accounts.activate(token.getAccountId(), now) == 0) {
                throw invalidToken(); // desfaz também a marcação do token
            }
            return;
        }

        boolean alreadyConfirmed = tokens.findByTokenHash(hash)
                .filter(token -> token.getUsedAt() != null)
                .flatMap(token -> accounts.findById(token.getAccountId()))
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .isPresent();
        if (!alreadyConfirmed) {
            throw invalidToken();
        }
    }

    private static BusinessException invalidToken() {
        return new BusinessException(AuthErrorCode.INVALID_VERIFICATION_TOKEN,
                "Invalid or expired verification link.");
    }
}
