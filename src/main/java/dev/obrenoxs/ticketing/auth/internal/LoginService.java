package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.platform.api.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class LoginService {

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final AttemptLimiter attemptLimiter;

    LoginService(AccountRepository accounts, PasswordHasher passwordHasher,
                 AttemptLimiter attemptLimiter) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.attemptLimiter = attemptLimiter;
    }

    public AuthenticatedAccount login(String rawEmail, String rawPassword) {
        String email = Account.normalizeEmail(rawEmail);

        Duration blockedFor = attemptLimiter.blockedFor(RateLimitPolicy.LOGIN_FAILURE, email);
        if (!blockedFor.isZero()) {
            throw new TooManyRequestsException(PlatformErrorCode.RATE_LIMITED,
                    "Too many failed attempts. Try again later.", blockedFor);
        }

        Optional<Account> found = accounts.findByEmail(email);
        boolean passwordMatches;
        if (found.isPresent()) {
            passwordMatches = passwordHasher.matches(rawPassword, found.get().getPasswordHash());
        } else {
            passwordHasher.simulateVerification(rawPassword);
            passwordMatches = false;
        }

        if (!passwordMatches || found.get().getStatus() != AccountStatus.ACTIVE) {
            attemptLimiter.recordFailure(RateLimitPolicy.LOGIN_FAILURE, email);
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Invalid email or password.");
        }

        Account account = found.get();
        return new AuthenticatedAccount(account.getId(), account.getRole());
    }
}
