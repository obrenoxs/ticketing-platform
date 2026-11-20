package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.api.OrganizerApprovals;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
class OrganizerApprovalChecker implements OrganizerApprovals {

    private final OrganizerProfileRepository profiles;

    OrganizerApprovalChecker(OrganizerProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isApproved(UUID accountId) {
        return profiles.existsByIdAndStatus(accountId, OrganizerStatus.APPROVED);
    }

    @Override
    public void requireApproved(UUID accountId) {
        if (!isApproved(accountId)) {
            throw new BusinessException(AuthErrorCode.ORGANIZER_NOT_APPROVED,
                    "Organizer account is not approved.");
        }
    }
}