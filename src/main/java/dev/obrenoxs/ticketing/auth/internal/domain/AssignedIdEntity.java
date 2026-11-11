package dev.obrenoxs.ticketing.auth.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@MappedSuperclass
abstract class AssignedIdEntity implements Persistable<UUID> {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {
    }

    protected AssignedIdEntity(UUID id) {
        this.id = Objects.requireNonNull(id);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof AssignedIdEntity that && Objects.equals(id, that.getId()));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}