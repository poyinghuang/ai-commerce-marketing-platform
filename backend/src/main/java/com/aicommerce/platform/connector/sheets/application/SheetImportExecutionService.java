package com.aicommerce.platform.connector.sheets.application;

import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import com.aicommerce.platform.product.application.AuditActorUnavailableException;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.application.ProductPreconditionFailedException;
import com.aicommerce.platform.product.application.ProductValidationException;
import org.springframework.stereotype.Service;

@Service
public class SheetImportExecutionService {
    private final AuditOperationContextFactory contexts;
    private final SheetImportJobLifecycleService lifecycle;
    private final SheetImportRowJpaRepository rows;
    private final SheetImportRowSuccessService success;
    private final SheetImportRowFailureService failure;
    private final SheetImportQueryService queries;

    public SheetImportExecutionService(AuditOperationContextFactory contexts,
            SheetImportJobLifecycleService lifecycle, SheetImportRowJpaRepository rows,
            SheetImportRowSuccessService success, SheetImportRowFailureService failure,
            SheetImportQueryService queries) {
        this.contexts = contexts;
        this.lifecycle = lifecycle;
        this.rows = rows;
        this.success = success;
        this.failure = failure;
        this.queries = queries;
    }

    public SheetImportView execute(UUID jobUuid, long expectedVersion, String requestId) {
        AuditOperationContext context = current(requestId);
        if (!lifecycle.begin(jobUuid, expectedVersion, context)) return queries.get(jobUuid);
        process(jobUuid, context);
        return lifecycle.finish(jobUuid, context);
    }

    public SheetImportView recoverInterrupted(UUID jobUuid) {
        AuditOperationContext context = contexts.forSystem("SHEET_IMPORT_RECOVERY");
        lifecycle.requireRecoverable(jobUuid);
        process(jobUuid, context);
        return lifecycle.finish(jobUuid, context);
    }

    private void process(UUID jobUuid, AuditOperationContext context) {
        for (UUID rowUuid : rows.findPendingRowUuids(jobUuid)) {
            try {
                success.execute(rowUuid, context);
            } catch (ProductPreconditionFailedException exception) {
                failure.record(rowUuid, "STALE_PRODUCT", "Product changed after preview");
            } catch (ProductArchivedException exception) {
                failure.record(rowUuid, "PRODUCT_ARCHIVED", "Archived Product cannot be imported");
            } catch (ProductValidationException exception) {
                failure.record(rowUuid, "VALIDATION_ERROR", "Product validation failed during execution");
            } catch (RuntimeException exception) {
                failure.record(rowUuid, "ROW_EXECUTION_FAILED", "Product row execution failed");
            }
        }
    }

    private AuditOperationContext current(String requestId) {
        try { return contexts.forCurrentActor(requestId); }
        catch (IllegalStateException exception) { throw new AuditActorUnavailableException(exception); }
    }
}
