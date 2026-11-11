package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(byte[] tokenHash);
}
