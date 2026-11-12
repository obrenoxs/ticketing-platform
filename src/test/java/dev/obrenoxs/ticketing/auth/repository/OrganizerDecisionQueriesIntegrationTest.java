package dev.obrenoxs.ticketing.auth.repository;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizerDecisionQueriesIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    @Autowired
    AccountRepository accounts;

    @Autowired
    OrganizerProfileRepository profiles;

    private static byte[] random(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private UUID newAccount(Role role) {
        UUID id = UUID.randomUUID();
        accounts.saveAndFlush(Account.register(id, id + "@example.com", "Test", "hash",
                random(40), 1, random(32), NOW, role));
        return id;
    }

    private UUID pendingOrganizer() {
        UUID id = newAccount(Role.ORGANIZER);
        profiles.saveAndFlush(OrganizerProfile.request(id, "Rock Producoes", NOW));
        return id;
    }

    @Test
    void shouldApproveOnlyOnce() {
        UUID organizer = pendingOrganizer();
        UUID admin = newAccount(Role.BUYER);

        assertThat(profiles.approveIfPending(organizer, admin, NOW)).isEqualTo(1);
        assertThat(profiles.approveIfPending(organizer, admin, NOW)).isZero();

        OrganizerProfile saved = profiles.findById(organizer).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrganizerStatus.APPROVED);
        assertThat(saved.getDecidedBy()).isEqualTo(admin);
        assertThat(saved.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldNotRejectAnAlreadyApprovedProfile() {
        UUID organizer = pendingOrganizer();
        UUID admin = newAccount(Role.BUYER);
        profiles.approveIfPending(organizer, admin, NOW);

        assertThat(profiles.rejectIfPending(organizer, admin, "late", NOW)).isZero();
        assertThat(profiles.findById(organizer).orElseThrow().getStatus())
                .isEqualTo(OrganizerStatus.APPROVED);
    }

    @Test
    void shouldRejectWithReason() {
        UUID organizer = pendingOrganizer();
        UUID admin = newAccount(Role.BUYER);

        assertThat(profiles.rejectIfPending(organizer, admin, "Incomplete data", NOW)).isEqualTo(1);

        OrganizerProfile saved = profiles.findById(organizer).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrganizerStatus.REJECTED);
        assertThat(saved.getRejectionReason()).isEqualTo("Incomplete data");
    }

    @Test
    void shouldReturnZeroForUnknownProfile() {
        assertThat(profiles.approveIfPending(UUID.randomUUID(), UUID.randomUUID(), NOW)).isZero();
    }

    @Test
    void shouldListOnlyPendingOldestFirst() {
        UUID organizer = pendingOrganizer();

        List<OrganizerProfile> pending = profiles.findByStatusOrderByCreatedAtAsc(
                OrganizerStatus.PENDING, PageRequest.of(0, 50));

        assertThat(pending).extracting(OrganizerProfile::getId).contains(organizer);
        assertThat(pending).allMatch(p -> p.getStatus() == OrganizerStatus.PENDING);
    }
}
