package dev.obrenoxs.ticketing.auth.api;

import java.util.UUID;

public interface OrganizerApprovals {

    boolean isApproved(UUID accountId);

    void requireApproved(UUID accountId);
}
