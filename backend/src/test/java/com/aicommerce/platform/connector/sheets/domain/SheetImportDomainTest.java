package com.aicommerce.platform.connector.sheets.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.application.ProductSheetMapping;
import com.aicommerce.platform.connector.sheets.application.SheetSource;
import com.aicommerce.platform.connector.sheets.application.SheetSnapshotFingerprint;
import org.junit.jupiter.api.Test;

class SheetImportDomainTest {

    private static final String HASH = "a".repeat(64);
    private static final SheetProductRowSnapshot SNAPSHOT = new SheetProductRowSnapshot(
            null, null, "SKU", "Product", null, null, null, null,
            null, null, null, null, null);

    @Test
    void canonicalMappingHasThirteenStableHeaders() {
        assertThat(ProductSheetMapping.HEADERS).hasSize(ProductSheetMapping.COLUMN_COUNT)
                .containsExactly("product_uuid", "product_id", "sku", "product_name", "brand", "category",
                        "subcategory", "short_description", "cost", "sale_price", "currency", "stock",
                        "product_url");
        assertThat(ProductSheetMapping.REQUIRED_HEADERS)
                .containsExactlyInAnyOrder("product_uuid", "product_id", "product_name");
        assertThat(ProductSheetMapping.MAX_DATA_ROWS).isEqualTo(1_000);
        assertThat(ProductSheetMapping.presenceMask(ProductSheetMapping.HEADERS)).isEqualTo(8191);
        assertThat(ProductSheetMapping.presenceMask(List.of("product_uuid", "product_id", "product_name")))
                .isEqualTo(11);
        assertThat(ProductSheetMapping.isPresent(11, "product_name")).isTrue();
        assertThat(ProductSheetMapping.isPresent(11, "sku")).isFalse();
        assertThatThrownBy(() -> ProductSheetMapping.presenceMask(List.of("product_uuid", "product_name")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductSheetMapping.presenceMask(
                List.of("product_uuid", "product_id", "product_name", "product_name")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fingerprintNormalizesLineEndingsAndOuterWhitespaceWithoutDelimiterCollisions() {
        String first = SheetSnapshotFingerprint.fingerprint(List.of(
                List.of(" product_name ", "Line 1\r\nLine 2"), List.of("ab", "c")));
        String normalized = SheetSnapshotFingerprint.fingerprint(List.of(
                List.of("product_name", "Line 1\nLine 2"), List.of("ab", "c")));
        String differentShape = SheetSnapshotFingerprint.fingerprint(List.of(
                List.of("product_name", "Line 1\nLine 2"), List.of("a", "bc")));

        assertThat(first).isEqualTo(normalized).matches("[0-9a-f]{64}");
        assertThat(differentShape).isNotEqualTo(first);
    }

    @Test
    void sourceAcceptsBoundedA1RangesAndRejectsUrlOrPathInjection() {
        assertThat(new SheetSource("sheet_123", "Products", "'Products'!A:M").range())
                .isEqualTo("'Products'!A:M");
        assertThat(new SheetSource("sheet_123", "Sheet 1", "'Sheet 1'!A2:M1001").range())
                .isEqualTo("'Sheet 1'!A2:M1001");
        assertThatThrownBy(() -> new SheetSource("sheet_123", "Products", "https://evil.example/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SheetSource("sheet_123", "Products", "Products!A:M/../../token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SheetSource("https://evil.example", "Products", "Products!A:M"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobStateRequiresCoherentCountsAndTerminalTransitions() {
        SheetImportJob job = SheetImportJob.previewed(
                UUID.randomUUID(), "sheet", "Products", "Products!A:M", HASH,
                ProductSheetMapping.ALL_HEADER_MASK, 2, 1, "local-admin");
        assertThat(job.getTotalRows()).isEqualTo(3);
        assertThat(job.startExecution()).isTrue();
        assertThat(job.startExecution()).isFalse();
        job.complete(1, 0, 1);
        assertThat(job.getStatus()).isEqualTo(SheetImportStatus.COMPLETED_WITH_ERRORS);
        assertThatThrownBy(() -> job.fail("FAILED", "late failure"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SheetImportJob.previewed(
                UUID.randomUUID(), "sheet", "Products", "Products!A:M", HASH,
                ProductSheetMapping.ALL_HEADER_MASK, 1_001, 0, "local-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 1000");
    }

    @Test
    void rowFactoriesKeepPresenceAndExecutionSemanticsExplicit() {
        UUID job = UUID.randomUUID();
        SheetImportRow create = SheetImportRow.create(UUID.randomUUID(), job, 2, HASH, SNAPSHOT);
        assertThat(create.getExecutionStatus()).isEqualTo(SheetImportExecutionStatus.PENDING);
        assertThat(create.getPlannedAction()).isEqualTo(SheetImportPlannedAction.CREATE);

        SheetImportRow invalid = SheetImportRow.invalid(
                UUID.randomUUID(), job, 3, "b".repeat(64), SheetImportMatchStrategy.NONE,
                null, null, SNAPSHOT,
                List.of(new SheetValidationError("product_name", "REQUIRED", "Product name is required")));
        assertThat(invalid.getExecutionStatus()).isEqualTo(SheetImportExecutionStatus.SKIPPED);
        assertThatThrownBy(() -> invalid.recordFailure("FAILED", "cannot execute"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SheetImportRow.update(
                UUID.randomUUID(), job, 4, HASH, SheetImportMatchStrategy.NONE,
                UUID.randomUUID(), 0, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SheetImportRow.create(UUID.randomUUID(), job, 1_002, HASH, SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 2 and 1001");
        assertThatThrownBy(() -> create.recordSuccess(UUID.randomUUID(), "INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROD-00000000");
        assertThat(create.getResultProductUuid()).isNull();
        assertThat(create.getResultProductId()).isNull();
        assertThat(create.getExecutionStatus()).isEqualTo(SheetImportExecutionStatus.PENDING);
    }
}
