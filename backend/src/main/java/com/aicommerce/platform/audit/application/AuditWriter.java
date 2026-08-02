package com.aicommerce.platform.audit.application;

import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditEvent;

public interface AuditWriter {
    UUID append(AuditEvent event);
}
