package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {

    List<OrganizerProfile> findByStatusOrderByCreatedAtAsc(OrganizerStatus status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("""
            update OrganizerProfile p
               set p.status = dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus.APPROVED,
                   p.decidedBy = :adminId,
                   p.decidedAt = :now
             where p.id = :accountId
               and p.status = dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus.PENDING
            """)
    int approveIfPending(@Param("accountId") UUID accountId,
                         @Param("adminId") UUID adminId,
                         @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("""
            update OrganizerProfile p
               set p.status = dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus.REJECTED,
                   p.decidedBy = :adminId,
                   p.decidedAt = :now,
                   p.rejectionReason = :reason
             where p.id = :accountId
               and p.status = dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus.PENDING
            """)
    int rejectIfPending(@Param("accountId") UUID accountId,
                        @Param("adminId") UUID adminId,
                        @Param("reason") String reason,
                        @Param("now") Instant now);
}
