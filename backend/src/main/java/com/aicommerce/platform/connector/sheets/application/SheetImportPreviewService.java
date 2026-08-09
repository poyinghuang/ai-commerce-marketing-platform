package com.aicommerce.platform.connector.sheets.application;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.product.application.AuditActorUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class SheetImportPreviewService {
    private final SheetValuesProvider provider;
    private final AuditOperationContextFactory contexts;
    private final SheetImportPreviewPersistenceService persistence;

    public SheetImportPreviewService(SheetValuesProvider provider, AuditOperationContextFactory contexts,
            SheetImportPreviewPersistenceService persistence) {
        this.provider = provider;
        this.contexts = contexts;
        this.persistence = persistence;
    }

    public SheetImportView preview(PreviewSheetImportCommand command, String requestId) {
        AuditOperationContext context;
        try {
            context = contexts.forCurrentActor(requestId);
        } catch (IllegalStateException exception) {
            throw new AuditActorUnavailableException(exception);
        }
        SheetSource source;
        try {
            source = command.source();
        } catch (IllegalArgumentException exception) {
            String field = exception.getMessage() != null && exception.getMessage().startsWith("spreadsheetId")
                    ? "spreadsheetId" : "range";
            String code = "spreadsheetId".equals(field) ? "INVALID_SPREADSHEET_ID" : "INVALID_SHEET_RANGE";
            throw new SheetImportValidationException(code, field, exception.getMessage());
        }
        SheetValuesSnapshot values = provider.read(source);
        return persistence.persist(source, values, context);
    }
}
