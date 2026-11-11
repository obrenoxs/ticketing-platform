package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByCpfHmac(byte[] cpfHmac);
}
