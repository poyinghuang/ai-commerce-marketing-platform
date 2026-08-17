package com.aicommerce.platform.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditActorType;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditSource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    @Column(name = "audit_uuid", nullable = false, updatable = false)
    private UUID auditUuid;

    @Column(name = "operation_uuid", nullable = false, updatable = false)
    private UUID operationUuid;

    @Column(name = "request_id", nullable = false, updatable = false, length = 128)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, updatable = false, length = 32)
    private AuditActorType actorType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 128)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 32)
    private AuditSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 64)
    private String entityType;

    @Column(name = "entity_uuid", nullable = false, updatable = false)
    private UUID entityUuid;

    @Column(name = "product_uuid", updatable = false)
    private UUID productUuid;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "stage4b_operation_ordinal", insertable = false, updatable = false)
    private Short stage4bOperationOrdinal;

    @OneToMany(mappedBy = "auditLog", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<AuditChangeEntity> changes = new ArrayList<>();

    protected AuditLogEntity() {
    }

    AuditLogEntity(AuditEvent event) {
        this.auditUuid = event.auditUuid();
        this.operationUuid = event.context().operationUuid();
        this.requestId = event.context().requestId();
        this.actorType = event.context().actor().type();
        this.actorId = event.context().actor().id();
        this.source = event.context().source();
        this.action = event.action();
        this.entityType = event.entityType();
        this.entityUuid = event.entityUuid();
        this.productUuid = event.productUuid();
        this.occurredAt = event.occurredAt();
    }

    void addChange(AuditChangeEntity change) {
        changes.add(change);
    }
}
