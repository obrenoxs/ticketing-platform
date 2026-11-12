package dev.obrenoxs.ticketing.auth.internal.web;

import dev.obrenoxs.ticketing.auth.internal.domain.OrganizerProfile;

import java.time.Instant;
import java.util.UUID;

public record PendingOrganizerResponse(UUID accountId, String publicName, Instant createdAt) {

    static PendingOrganizerResponse from(OrganizerProfile profile) {
        return new PendingOrganizerResponse(profile.getId(), profile.getPublicName(), profile.getCreatedAt());
    }
}
