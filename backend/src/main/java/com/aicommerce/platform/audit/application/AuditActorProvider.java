package com.aicommerce.platform.audit.application;

import com.aicommerce.platform.audit.domain.AuditActor;

public interface AuditActorProvider {
    AuditActor currentActor();
}
