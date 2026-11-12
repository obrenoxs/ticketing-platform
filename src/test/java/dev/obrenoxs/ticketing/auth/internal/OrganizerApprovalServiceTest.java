package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrganizerApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final UUID ORGANIZER = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private final OrganizerProfileRepository profiles = mock(OrganizerProfileRepository.class);
    private final OrganizerApprovalService service = new OrganizerApprovalService(profiles, () -> NOW);

    @Test
    void shouldApproveWhenStillPending() {
        when(profiles.approveIfPending(ORGANIZER, ADMIN, NOW)).thenReturn(1);

        service.approve(ORGANIZER, ADMIN);

        verify(profiles).approveIfPending(ORGANIZER, ADMIN, NOW);
    }

    @Test
    void shouldReport409WhenProfileWasAlreadyDecided() {
        when(profiles.approveIfPending(ORGANIZER, ADMIN, NOW)).thenReturn(0);
        when(profiles.existsById(ORGANIZER)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(ORGANIZER, ADMIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.ORGANIZER_ALREADY_DECIDED));
    }

    @Test
    void shouldReport404WhenProfileDoesNotExist() {
        when(profiles.approveIfPending(ORGANIZER, ADMIN, NOW)).thenReturn(0);
        when(profiles.existsById(ORGANIZER)).thenReturn(false);

        assertThatThrownBy(() -> service.approve(ORGANIZER, ADMIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.ORGANIZER_NOT_FOUND));
    }

    @Test
    void shouldTrimTheRejectionReason() {
        when(profiles.rejectIfPending(ORGANIZER, ADMIN, "Incomplete data", NOW)).thenReturn(1);

        service.reject(ORGANIZER, ADMIN, "  Incomplete data  ");

        verify(profiles).rejectIfPending(ORGANIZER, ADMIN, "Incomplete data", NOW);
    }

    @Test
    void shouldCapThePageSizeAndNeverUseNegativePages() {
        service.listPending(-3, 100000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(profiles).findByStatusOrderByCreatedAtAsc(eq(OrganizerStatus.PENDING), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }
}
