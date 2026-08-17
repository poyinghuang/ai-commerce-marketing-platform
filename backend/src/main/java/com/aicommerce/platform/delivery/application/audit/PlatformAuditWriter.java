package com.aicommerce.platform.delivery.application.audit;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
public interface PlatformAuditWriter { void write(PlatformAuditEvent event, AuditOperationContext context); }
