package com.aicommerce.platform.connector.sheets.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditActor;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditSource;
import com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.PatchField;
import com.aicommerce.platform.product.application.PatchProductCommand;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SheetImportExecutionIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired SheetImportPreviewService previews;
    @Autowired SheetImportPreviewPersistenceService previewPersistence;
    @Autowired SheetImportExecutionService executions;
    @Autowired SheetImportJobLifecycleService lifecycle;
    @Autowired SheetImportRowSuccessService rowSuccess;
    @Autowired SheetImportJobJpaRepository jobs;
    @Autowired SheetImportRowJpaRepository rows;
    @Autowired ProductCommandService productCommands;
    @Autowired ProductJpaRepository products;
    @Autowired ProductQualityQueryService quality;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SheetValuesProvider provider;

    @Test
    void previewPersistsPresenceAndExecuteCreatesUpdatesAndIsIdempotent() {
        Product existing = product("sheet-existing", "SKU-KEEP", "Old name", "Old brand");
        SheetValuesSnapshot snapshot = snapshot(existing, true);
        when(provider.read(source())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return snapshot;
        });

        SheetImportView preview = previews.preview(command(), "sheet-preview-request");

        assertThat(preview.headerPresenceMask()).isEqualTo(27);
        assertThat(preview.totalRows()).isEqualTo(4);
        assertThat(preview.validRows()).isEqualTo(2);
        assertThat(preview.invalidRows()).isEqualTo(2);
        assertThat(preview.rows()).extracting(SheetImportView.Row::plannedAction)
                .containsExactly("UPDATE", "CREATE", "INVALID", "INVALID");
        assertThat(auditCount("sheet-preview-request", "SHEET_IMPORT_JOB")).isEqualTo(1);

        SheetImportView result = executions.execute(preview.importJobUuid(), preview.version(), "sheet-execute-request");

        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        Product updated = products.findById(existing.getProductUuid()).orElseThrow();
        assertThat(updated.getProductName()).isEqualTo("Updated name");
        assertThat(updated.getBrand()).isNull();
        assertThat(updated.getSku()).isEqualTo("SKU-KEEP");
        UUID createdUuid = result.rows().stream().filter(row -> "CREATE".equals(row.plannedAction()))
                .findFirst().orElseThrow().resultProductUuid();
        assertThat(products.findById(createdUuid).orElseThrow().getBrand()).isEqualTo("New brand");
        assertThat(quality.get(createdUuid).productMasterScore()).isEqualTo(12);
        assertThat(auditCount("sheet-execute-request", "PRODUCT")).isEqualTo(2);

        int auditsBeforeRepeat = auditCount("sheet-execute-request", null);
        SheetImportView repeated = executions.execute(result.importJobUuid(), result.version(), "sheet-repeat-request");
        assertThat(repeated.importJobUuid()).isEqualTo(result.importJobUuid());
        assertThat(repeated.status()).isEqualTo(result.status());
        assertThat(repeated.version()).isEqualTo(result.version());
        assertThat(repeated.rows()).isEqualTo(result.rows());
        assertThat(auditCount("sheet-execute-request", null)).isEqualTo(auditsBeforeRepeat);
        assertThat(auditCount("sheet-repeat-request", null)).isZero();
        assertThatThrownBy(() -> executions.execute(result.importJobUuid(), preview.version(), "sheet-stale-job"))
                .isInstanceOf(SheetImportPreconditionFailedException.class);
    }

    @Test
    void staleProductFailsOnlyItsRowAndLeavesOtherRowsCommitted() {
        Product existing = product("sheet-stale-existing", "SKU-STABLE", "Before", "Brand");
        when(provider.read(source())).thenReturn(snapshot(existing, false));
        SheetImportView preview = previews.preview(command(), "sheet-stale-preview");
        productCommands.patch(existing.getProductUuid(), existing.getVersion(), brandPatch("Changed elsewhere"),
                "sheet-concurrent-change");

        SheetImportView result = executions.execute(preview.importJobUuid(), preview.version(), "sheet-partial-execute");

        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.updatedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.rows()).filteredOn(row -> "FAILED".equals(row.executionStatus()))
                .singleElement().extracting(SheetImportView.Row::executionErrorCode).isEqualTo("STALE_PRODUCT");
        assertThat(products.findById(existing.getProductUuid()).orElseThrow().getProductName()).isEqualTo("Before");
        assertThat(auditCount("sheet-partial-execute", "PRODUCT")).isEqualTo(1);
    }

    @Test
    void interruptedJobRecoveryUsesOneNonemptySystemOperationForPendingRows() {
        when(provider.read(source())).thenReturn(new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name"),
                List.of("", "", "Recovered product"))));
        SheetImportView preview = previews.preview(command(), "sheet-recovery-preview");
        lifecycle.begin(preview.importJobUuid(), preview.version(), contexts.forCurrentActor("sheet-recovery-begin"));

        SheetImportView result = executions.recoverInterrupted(preview.importJobUuid());

        assertThat(result.status()).isEqualTo("COMPLETED");
        List<java.util.Map<String, Object>> systemAudits = jdbc.queryForList("""
                select operation_uuid, request_id, actor_type, actor_id, source
                from audit_logs where actor_id='SHEET_IMPORT_RECOVERY'
                """);
        assertThat(systemAudits).isNotEmpty();
        assertThat(systemAudits).extracting(row -> row.get("actor_type")).containsOnly("SYSTEM");
        assertThat(systemAudits).extracting(row -> row.get("source")).containsOnly("SYSTEM");
        assertThat(systemAudits).extracting(row -> row.get("operation_uuid")).doesNotContainNull()
                .containsOnly(systemAudits.getFirst().get("operation_uuid"));
        assertThat(systemAudits).extracting(row -> row.get("request_id")).doesNotContainNull()
                .containsOnly(systemAudits.getFirst().get("request_id"));
    }

    @Test
    void fingerprintIgnoresOnlyTrailingBlankRows() {
        when(provider.read(source())).thenReturn(new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name", "", ""),
                List.of("", "", "Stable", "", ""), List.of("", "", ""), List.of())));
        String first = previews.preview(command(), "sheet-fingerprint-one").sourceFingerprint();
        when(provider.read(source())).thenReturn(new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name"), List.of("", "", "Stable"))));
        String second = previews.preview(command(), "sheet-fingerprint-two").sourceFingerprint();
        assertThat(first).isEqualTo(second);

        when(provider.read(source())).thenReturn(new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name"),
                List.of("", "", "", "undeclared"))));
        assertThatThrownBy(() -> previews.preview(command(), "sheet-extra-column"))
                .isInstanceOfSatisfying(SheetImportValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_SHEET_HEADER"));
    }

    @Test
    void previewAuditFailureRollsBackJobAndRows() {
        long jobsBefore = jobs.count();
        long rowsBefore = rows.count();
        AuditOperationContext invalid = new AuditOperationContext(UUID.randomUUID(), "invalid request id",
                AuditActor.localAdmin(), AuditSource.API);
        SheetValuesSnapshot snapshot = new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name"),
                List.of("", "", "Rollback preview")));

        assertThatThrownBy(() -> previewPersistence.persist(source(), snapshot, invalid))
                .isInstanceOf(RuntimeException.class);
        assertThat(jobs.count()).isEqualTo(jobsBefore);
        assertThat(rows.count()).isEqualTo(rowsBefore);
    }

    @Test
    void productAuditFailureRollsBackProductQualityAndRowResultTogether() {
        when(provider.read(source())).thenReturn(new SheetValuesSnapshot(List.of(
                List.of("product_uuid", "product_id", "product_name"),
                List.of("", "", "Rollback execute"))));
        SheetImportView preview = previews.preview(command(), "sheet-row-rollback-preview");
        lifecycle.begin(preview.importJobUuid(), preview.version(), contexts.forCurrentActor("sheet-row-rollback-begin"));
        UUID rowUuid = preview.rows().getFirst().importRowUuid();
        long productsBefore = products.count();
        AuditOperationContext invalid = new AuditOperationContext(UUID.randomUUID(), "invalid request id",
                AuditActor.localAdmin(), AuditSource.API);

        assertThatThrownBy(() -> rowSuccess.execute(rowUuid, invalid)).isInstanceOf(RuntimeException.class);
        assertThat(products.count()).isEqualTo(productsBefore);
        assertThat(rows.findById(rowUuid).orElseThrow().getExecutionStatus())
                .isEqualTo(SheetImportExecutionStatus.PENDING);
        assertThat(auditCount("invalid request id", null)).isZero();
    }

    private SheetValuesSnapshot snapshot(Product existing, boolean invalidRows) {
        List<List<String>> values = new java.util.ArrayList<>();
        values.add(List.of("product_uuid", "product_id", "product_name", "brand"));
        values.add(List.of(existing.getProductUuid().toString(), existing.getProductId(), "Updated name", ""));
        values.add(List.of("", "", "Created name", "New brand"));
        if (invalidRows) {
            values.add(List.of(UUID.randomUUID().toString(), existing.getProductId(), "Unknown", ""));
            values.add(List.of(existing.getProductUuid().toString(), existing.getProductId(), "Duplicate", ""));
            values.add(List.of("", "", "", ""));
        }
        return new SheetValuesSnapshot(values);
    }

    private Product product(String requestId, String sku, String name, String brand) {
        return productCommands.create(new CreateProductCommand(sku, name, brand, "Category", null, null,
                BigDecimal.ONE, BigDecimal.TEN, "TWD", 5L, "https://example.com/product"), requestId);
    }

    private PatchProductCommand brandPatch(String brand) {
        PatchField<String> absent = PatchField.absent();
        return new PatchProductCommand(absent, absent, PatchField.present(brand), absent, absent, absent,
                PatchField.absent(), PatchField.absent(), absent, PatchField.absent(), absent);
    }

    private PreviewSheetImportCommand command() {
        return new PreviewSheetImportCommand("stub-products", "Products", null);
    }

    private SheetSource source() {
        return command().source();
    }

    private int auditCount(String requestId, String entityType) {
        if (entityType == null) {
            return jdbc.queryForObject("select count(*) from audit_logs where request_id=?", Integer.class, requestId);
        }
        return jdbc.queryForObject("select count(*) from audit_logs where request_id=? and entity_type=?",
                Integer.class, requestId, entityType);
    }
}
