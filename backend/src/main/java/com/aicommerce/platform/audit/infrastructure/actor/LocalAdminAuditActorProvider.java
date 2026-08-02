package com.aicommerce.platform.audit.infrastructure.actor;

import com.aicommerce.platform.audit.application.AuditActorProvider;
import com.aicommerce.platform.audit.domain.AuditActor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class LocalAdminAuditActorProvider implements AuditActorProvider {

    @Override
    public AuditActor currentActor() {
        return AuditActor.localAdmin();
    }
}
