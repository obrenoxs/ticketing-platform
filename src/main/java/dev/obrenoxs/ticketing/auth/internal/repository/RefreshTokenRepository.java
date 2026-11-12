package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken t SET t.usedAt = :now
        WHERE t.tokenHash = :hash
          AND t.usedAt IS NULL
          AND t.revokedAt IS NULL
          AND t.expiresAt > :now
        """)
    int markUsed(@Param("hash") byte[] hash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken t SET t.revokedAt = :now
        WHERE t.familyId = :familyId
          AND t.revokedAt IS NULL
        """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
