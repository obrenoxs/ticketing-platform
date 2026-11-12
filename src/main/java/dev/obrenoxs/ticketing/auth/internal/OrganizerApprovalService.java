package dev.obrenoxs.ticketing.auth.internal;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;
import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerStatus;
import dev.obrenoxs.ticketing.auth.internal.repository.OrganizerProfileRepository;
import dev.obrenoxs.ticketing.platform.api.BusinessException;
import dev.obrenoxs.ticketing.platform.api.DatabaseClock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizerApprovalService {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrganizerProfileRepository profiles;
    private final DatabaseClock clock;

    OrganizerApprovalService(OrganizerProfileRepository profiles, DatabaseClock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OrganizerProfile> listPending(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return profiles.findByStatusOrderByCreatedAtAsc(OrganizerStatus.PENDING, pageable);
    }

    @Transactional
    public void approve(UUID organizerId, UUID adminId) {
        int updated = profiles.approveIfPending(organizerId, adminId, clock.now());
        if (updated == 0) {
            throw whyNotUpdated(organizerId);
        }
    }

    @Transactional
    public void reject(UUID organizerId, UUID adminId, String reason) {
        int updated = profiles.rejectIfPending(organizerId, adminId, reason.trim(), clock.now());
        if (updated == 0) {
            throw whyNotUpdated(organizerId);
        }
    }

    private BusinessException whyNotUpdated(UUID organizerId) {
        if (profiles.existsById(organizerId)) {
            return new BusinessException(AuthErrorCode.ORGANIZER_ALREADY_DECIDED,
                    "This organizer request was already decided.");
        }
        return new BusinessException(AuthErrorCode.ORGANIZER_NOT_FOUND,
                "Organizer request not found.");
    }
}