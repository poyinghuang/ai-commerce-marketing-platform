package com.aicommerce.platform.audit.application;

import java.util.UUID;
import java.util.regex.Pattern;

import com.aicommerce.platform.audit.domain.AuditActor;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditSource;
import org.springframework.stereotype.Component;

@Component
public class AuditOperationContextFactory {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final AuditActorProvider actorProvider;

    public AuditOperationContextFactory(AuditActorProvider actorProvider) {
        this.actorProvider = actorProvider;
    }

    public AuditOperationContext forCurrentActor(String serverResolvedRequestId) {
        return new AuditOperationContext(
                UUID.randomUUID(),
                safeRequestId(serverResolvedRequestId),
                actorProvider.currentActor(),
                AuditSource.API);
    }

    public AuditOperationContext forSystem(String systemActorId) {
        return new AuditOperationContext(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                AuditActor.system(systemActorId),
                AuditSource.SYSTEM);
    }

    public AuditOperationContext forStableOperation(UUID operationUuid, AuditOperationContext trustedContext) {
        return new AuditOperationContext(
                operationUuid,
                trustedContext.requestId(),
                trustedContext.actor(),
                trustedContext.source());
    }

    private String safeRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
