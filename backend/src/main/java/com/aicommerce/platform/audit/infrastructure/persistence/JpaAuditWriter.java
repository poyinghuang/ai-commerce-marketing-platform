package com.aicommerce.platform.audit.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditValueSanitizer;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditEvent;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaAuditWriter implements AuditWriter {

    private final EntityManager entityManager;
    private final AuditValueSanitizer sanitizer;

    public JpaAuditWriter(EntityManager entityManager, AuditValueSanitizer sanitizer) {
        this.entityManager = entityManager;
        this.sanitizer = sanitizer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(AuditEvent event) {
        AuditLogEntity auditLog = new AuditLogEntity(event);
        event.changes().stream()
                .map(sanitizer::sanitize)
                .map(change -> new AuditChangeEntity(auditLog, change))
                .forEach(auditLog::addChange);
        entityManager.persist(auditLog);
        return event.auditUuid();
    }
}
