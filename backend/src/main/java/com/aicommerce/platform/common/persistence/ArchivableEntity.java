package com.aicommerce.platform.common.persistence;

import java.time.Instant;
import java.util.Objects;

import com.aicommerce.platform.common.domain.LifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class ArchivableEntity extends MutableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public boolean archive(Instant archivedAt) {
        Instant requiredArchivedAt = Objects.requireNonNull(archivedAt, "archivedAt is required");
        if (lifecycleStatus == LifecycleStatus.ARCHIVED) {
            return false;
        }
        this.lifecycleStatus = LifecycleStatus.ARCHIVED;
        this.archivedAt = requiredArchivedAt;
        return true;
    }

    public boolean restore() {
        if (lifecycleStatus == LifecycleStatus.ACTIVE) {
            return false;
        }
        this.lifecycleStatus = LifecycleStatus.ACTIVE;
        this.archivedAt = null;
        return true;
    }

    public LifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
