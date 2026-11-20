package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.AbstractIntegrationTest;
import dev.obrenoxs.ticketing.auth.api.OrganizerApprovals;
import dev.obrenoxs.ticketing.auth.internal.domain.Account;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.Role;
import dev.obrenoxs.ticketing.auth.internal.repository.AccountRepository;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrganizerApprovalsIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    OrganizerApprovals approvals;

    @Autowired
    OrganizerApprovalService decisions;

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
    void shouldNotApproveWhilePending() {
        UUID organizer = pendingOrganizer();

        assertThat(approvals.isApproved(organizer)).isFalse();
        assertThatThrownBy(() -> approvals.requireApproved(organizer))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.ORGANIZER_NOT_APPROVED));
    }

    @Test
    void shouldReflectTheDecisionImmediately() {
        UUID organizer = pendingOrganizer();
        UUID admin = newAccount(Role.BUYER); // so precisamos de um id de conta valido (FK de decided_by)
        assertThat(approvals.isApproved(organizer)).isFalse();

        decisions.approve(organizer, admin);

        assertThat(approvals.isApproved(organizer)).isTrue();
        assertThatCode(() -> approvals.requireApproved(organizer)).doesNotThrowAnyException();
    }

    @Test
    void shouldNotApproveARejectedOrganizer() {
        UUID organizer = pendingOrganizer();
        decisions.reject(organizer, newAccount(Role.BUYER), "Incomplete data");

        assertThat(approvals.isApproved(organizer)).isFalse();
    }

    @Test
    void shouldNotApproveABuyerOrAnUnknownAccount() {
        assertThat(approvals.isApproved(newAccount(Role.BUYER))).isFalse();
        assertThat(approvals.isApproved(UUID.randomUUID())).isFalse();
    }
}
