package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCpfHmac(byte[] cpfHmac);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Account a
        set a.status = dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus.ACTIVE,
            a.updatedAt = :now
        where a.id = :id
          and a.status = dev.obrenoxs.ticketing.auth.internal.domain.AccountStatus.PENDING_EMAIL
        """)
    int activate(@Param("id") UUID id, @Param("now") Instant now);
}
