package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizerApprovalCheckerTest {

    private static final UUID ID = UUID.randomUUID();

    private final OrganizerProfileRepository profiles = mock(OrganizerProfileRepository.class);
    private final OrganizerApprovalChecker checker = new OrganizerApprovalChecker(profiles);

    @Test
    void shouldReportApprovedOnlyWhenTheDatabaseSaysApproved() {
        when(profiles.existsByIdAndStatus(ID, OrganizerStatus.APPROVED)).thenReturn(true);

        assertThat(checker.isApproved(ID)).isTrue();
        assertThat(checker.isApproved(UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldLetAnApprovedOrganizerThrough() {
        when(profiles.existsByIdAndStatus(ID, OrganizerStatus.APPROVED)).thenReturn(true);

        assertThatCode(() -> checker.requireApproved(ID)).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseWith403WhenNotApproved() {
        assertThatThrownBy(() -> checker.requireApproved(ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.ORGANIZER_NOT_APPROVED));
    }
}
