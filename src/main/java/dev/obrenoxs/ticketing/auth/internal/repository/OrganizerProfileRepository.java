package dev.obrenoxs.ticketing.auth.internal.repository;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {
}
