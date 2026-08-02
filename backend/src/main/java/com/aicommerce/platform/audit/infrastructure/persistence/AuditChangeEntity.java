package com.aicommerce.platform.audit.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log_changes")
class AuditChangeEntity {

    @Id
    @Column(name = "audit_change_uuid", nullable = false, updatable = false)
    private UUID auditChangeUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "audit_uuid", nullable = false, updatable = false)
    private AuditLogEntity auditLog;

    @Column(name = "field_name", nullable = false, updatable = false, length = 128)
    private String fieldName;

    @Column(name = "old_value", updatable = false, length = 4096)
    private String oldValue;

    @Column(name = "new_value", updatable = false, length = 4096)
    private String newValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, updatable = false, length = 32)
    private AuditValueType valueType;

    @Column(name = "change_order", nullable = false, updatable = false)
    private short changeOrder;

    protected AuditChangeEntity() {
    }

    AuditChangeEntity(AuditLogEntity auditLog, AuditChange change) {
        this.auditChangeUuid = UUID.randomUUID();
        this.auditLog = auditLog;
        this.fieldName = change.fieldName();
        this.oldValue = change.oldValue();
        this.newValue = change.newValue();
        this.valueType = change.valueType();
        this.changeOrder = (short) change.changeOrder();
    }
}
