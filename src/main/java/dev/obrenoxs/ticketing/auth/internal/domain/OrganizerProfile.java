package dev.obrenoxs.ticketing.auth.internal.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizer_profile")
@AttributeOverride(name = "id", column = @Column(name = "account_id"))
public class OrganizerProfile extends AssignedIdEntity {

    @Column(name = "public_name", nullable = false, length = 150)
    private String publicName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizerStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private OrganizerProfile(UUID accountId) {
        super(accountId);
    }

    public static OrganizerProfile request(UUID accountId, String publicName, Instant now) {
        OrganizerProfile profile = new OrganizerProfile(accountId);
        profile.publicName = publicName.trim();
        profile.status = OrganizerStatus.PENDING;
        profile.createdAt = now;
        return profile;
    }

    public void approve(UUID adminId, Instant now) {
        requirePending();
        this.status = OrganizerStatus.APPROVED;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public void reject(UUID adminId, String reason, Instant now) {
        requirePending();
        this.status = OrganizerStatus.REJECTED;
        this.decidedBy = adminId;
        this.decidedAt = now;
        this.rejectionReason = reason;
    }

    private void requirePending() {
        if (status != OrganizerStatus.PENDING) {
            throw new IllegalStateException("Organizer request was already decided.");
        }
    }

    public String getPublicName() {
        return publicName;
    }

    public OrganizerStatus getStatus() {
        return status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
