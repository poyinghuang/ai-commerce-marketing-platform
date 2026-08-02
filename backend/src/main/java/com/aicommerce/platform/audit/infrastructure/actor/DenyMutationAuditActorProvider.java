package com.aicommerce.platform.audit.infrastructure.actor;

import com.aicommerce.platform.audit.application.AuditActorProvider;
import com.aicommerce.platform.audit.domain.AuditActor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production | (!local & !test)")
public class DenyMutationAuditActorProvider implements AuditActorProvider {

    @Override
    public AuditActor currentActor() {
        throw new IllegalStateException("A trusted production AuditActorProvider is required for mutations");
    }
}
