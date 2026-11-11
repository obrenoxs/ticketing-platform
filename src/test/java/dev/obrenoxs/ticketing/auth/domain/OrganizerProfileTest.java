package dev.obrenoxs.ticketing.auth.domain;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizerProfileTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");
    private final UUID admin = UUID.randomUUID();

    @Test
    void shouldStartPending() {
        OrganizerProfile p = OrganizerProfile.request(UUID.randomUUID(), " Produtora X ", NOW);
        assertThat(p.getStatus()).isEqualTo(OrganizerStatus.PENDING);
        assertThat(p.getPublicName()).isEqualTo("Produtora X");
    }

    @Test
    void shouldApprove() {
        OrganizerProfile p = OrganizerProfile.request(UUID.randomUUID(), "X", NOW);
        p.approve(admin, NOW);
        assertThat(p.getStatus()).isEqualTo(OrganizerStatus.APPROVED);
        assertThat(p.getDecidedBy()).isEqualTo(admin);
    }

    @Test
    void shouldRejectWithReason() {
        OrganizerProfile p = OrganizerProfile.request(UUID.randomUUID(), "X", NOW);
        p.reject(admin, "incomplete data", NOW);
        assertThat(p.getStatus()).isEqualTo(OrganizerStatus.REJECTED);
        assertThat(p.getRejectionReason()).isEqualTo("incomplete data");
    }

    @Test
    void shouldNotDecideTwice() {
        OrganizerProfile p = OrganizerProfile.request(UUID.randomUUID(), "X", NOW);
        p.approve(admin, NOW);
        assertThatThrownBy(() -> p.reject(admin, "late", NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
