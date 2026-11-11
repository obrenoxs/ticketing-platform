package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(byte[] tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update EmailVerificationToken t
        set t.usedAt = :now
        where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now
        """)
    int markUsed(@Param("hash") byte[] hash, @Param("now") Instant now);
}
