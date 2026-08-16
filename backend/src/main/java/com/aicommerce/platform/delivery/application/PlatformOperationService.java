package com.aicommerce.platform.delivery.application;

import java.time.Duration;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.delivery.application.port.PlatformAdPort;
import com.aicommerce.platform.delivery.application.port.PlatformAdSetPort;
import com.aicommerce.platform.delivery.application.port.PlatformCampaignPort;
import com.aicommerce.platform.delivery.application.port.PlatformCommand;
import com.aicommerce.platform.delivery.domain.OperationOutcome;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperation;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.ReconciliationResult;
import org.springframework.stereotype.Service;

@Service
public class PlatformOperationService {
    static final Duration SUBMISSION_LEASE = Duration.ofMinutes(5);
    private final PlatformOperationTransactions transactions;
    private final PlatformOperationInputCanonicalizer canonicalizer;
    private final PlatformCampaignPort campaigns;
    private final PlatformAdSetPort adSets;
    private final PlatformAdPort ads;

    public PlatformOperationService(PlatformOperationTransactions transactions,
            PlatformOperationInputCanonicalizer canonicalizer, PlatformCampaignPort campaigns,
            PlatformAdSetPort adSets, PlatformAdPort ads) {
        this.transactions = transactions;
        this.canonicalizer = canonicalizer;
        this.campaigns = campaigns;
        this.adSets = adSets;
        this.ads = ads;
    }

    public PlatformOperation create(CreatePlatformOperationCommand command, AuditOperationContext context) {
        var input = canonicalizer.canonicalize(command.normalizedRequestJson());
        String scope = command.platformAccountUuid() + ":" + context.actor().type() + ":"
                + context.actor().id() + ":" + command.clientRequestUuid();
        return transactions.createOrReplay(command, input, canonicalizer.idempotencyKey(scope), context);
    }

    public PlatformOperation execute(UUID operationUuid, long expectedVersion, AuditOperationContext context) {
        PlatformCommand command = transactions.claim(operationUuid, expectedVersion, context);
        OperationOutcome outcome;
        try {
            outcome = submit(command);
            if (outcome == null) outcome = new OperationOutcome.Unknown("fake-null-outcome");
        } catch (RuntimeException exception) {
            // Once submission starts, an unclassified adapter exception is ambiguous and must reconcile.
            outcome = new OperationOutcome.Unknown("provider-exception");
        }
        return transactions.recordOutcome(operationUuid, outcome, context);
    }

    public PlatformOperation reconcile(UUID operationUuid, AuditOperationContext context) {
        PlatformOperation operation = transactions.get(operationUuid);
        if (operation.getStatus() != PlatformOperationStatus.UNKNOWN_OUTCOME) {
            throw new PlatformOperationConflictException("PLATFORM_RECONCILIATION_NOT_REQUIRED",
                    "Only an unknown outcome may be reconciled");
        }
        ReconciliationResult result = reconcile(operation.getEntityType(), operationUuid);
        return transactions.recordReconciliation(operationUuid, result, context);
    }

    public PlatformOperation recoverExpiredSubmission(UUID operationUuid, long expectedVersion,
            AuditOperationContext context) {
        return transactions.recoverExpiredSubmission(operationUuid, expectedVersion, SUBMISSION_LEASE, context);
    }

    private OperationOutcome submit(PlatformCommand command) {
        return switch (command.entityType()) {
            case CAMPAIGN -> campaigns.submit(command);
            case AD_SET -> adSets.submit(command);
            case AD -> ads.submit(command);
        };
    }

    private ReconciliationResult reconcile(PlatformEntityType type, UUID operationUuid) {
        return switch (type) {
            case CAMPAIGN -> campaigns.reconcile(operationUuid);
            case AD_SET -> adSets.reconcile(operationUuid);
            case AD -> ads.reconcile(operationUuid);
        };
    }
}
