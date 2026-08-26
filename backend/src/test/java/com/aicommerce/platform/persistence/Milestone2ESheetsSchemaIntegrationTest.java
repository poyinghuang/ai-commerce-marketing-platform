package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus;
import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.domain.SheetImportMatchStrategy;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import com.aicommerce.platform.connector.sheets.domain.SheetImportStatus;
import com.aicommerce.platform.connector.sheets.domain.SheetProductRowSnapshot;
import com.aicommerce.platform.connector.sheets.domain.SheetValidationError;
import com.aicommerce.platform.connector.sheets.application.ProductSheetMapping;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone2ESheetsSchemaIntegrationTest {

    private static final String HASH = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired SheetImportJobJpaRepository jobs;
    @Autowired SheetImportRowJpaRepository rows;

    @Test
    void sheetsSchemaAndJpaMappingsRemainValidAfterV7() {
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(List.of("sheet_import_jobs", "sheet_import_rows")).allMatch(this::tableExists);
        assertThat(List.of(jobs, rows)).doesNotContainNull();
    }

    @Test
    void jobEnumsFingerprintCountsAndTerminalStateAreDatabaseEnforced() {
        UUID job = insertJob(1, 0);

        assertThatThrownBy(() -> insertRawJob(UUID.randomUUID(), "OTHER", HASH, "PREVIEWED", 1, 1, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawJob(UUID.randomUUID(), "GOOGLE_SHEETS", HASH, "UNKNOWN", 1, 1, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawJob(UUID.randomUUID(), "GOOGLE_SHEETS", "ABC", "PREVIEWED", 1, 1, 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawJob(UUID.randomUUID(), "GOOGLE_SHEETS", HASH,
                "PREVIEWED", 1, 1, 0, 7)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawJob(UUID.randomUUID(), "GOOGLE_SHEETS", HASH,
                "PREVIEWED", 1, 1, 0, 8192)).isInstanceOf(DataAccessException.class);
        assertRejected("UPDATE sheet_import_jobs SET total_rows = -1 WHERE import_job_uuid = ?", job);
        assertRejected("UPDATE sheet_import_jobs SET total_rows = 1001, valid_rows = 1001 "
                + "WHERE import_job_uuid = ?", job);
        assertRejected("UPDATE sheet_import_jobs SET valid_rows = 2 WHERE import_job_uuid = ?", job);
        assertRejected("UPDATE sheet_import_jobs SET status = 'COMPLETED' WHERE import_job_uuid = ?", job);
        assertRejected("UPDATE sheet_import_jobs SET status = 'FAILED' WHERE import_job_uuid = ?", job);
        assertRejected("UPDATE sheet_import_jobs SET header_presence_mask = 11 WHERE import_job_uuid = ?", job);

        jdbc.update("UPDATE sheet_import_jobs SET status = 'EXECUTING', updated_at = CURRENT_TIMESTAMP, version = 1 "
                + "WHERE import_job_uuid = ?", job);
        jdbc.update("UPDATE sheet_import_jobs SET status = 'COMPLETED', created_count = 1, "
                + "updated_at = CURRENT_TIMESTAMP, version = 2 WHERE import_job_uuid = ?", job);
        assertThat(jdbc.queryForObject("SELECT status FROM sheet_import_jobs WHERE import_job_uuid = ?",
                String.class, job)).isEqualTo("COMPLETED");
    }

    @Test
    void rowPlanJsonExecutionAndForeignKeysAreDatabaseEnforced() {
        UUID job = insertJob(1, 0);
        UUID product = insertProduct("ROW-CONSTRAINT");
        UUID row = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sheet_import_rows
                    (import_row_uuid, import_job_uuid, row_number, source_row_hash,
                     planned_action, match_strategy, product_name)
                VALUES (?, ?, 2, ?, 'CREATE', 'NONE', 'Product')
                """, row, job, HASH);

        assertRejected("INSERT INTO sheet_import_rows "
                        + "(import_row_uuid, import_job_uuid, row_number, source_row_hash, planned_action, match_strategy) "
                        + "VALUES (?, ?, 1, ?, 'CREATE', 'NONE')",
                UUID.randomUUID(), job, HASH);
        assertRejected("INSERT INTO sheet_import_rows "
                        + "(import_row_uuid, import_job_uuid, row_number, source_row_hash, planned_action, match_strategy) "
                        + "VALUES (?, ?, 1002, ?, 'CREATE', 'NONE')",
                UUID.randomUUID(), job, HASH);
        assertThatThrownBy(() -> insertRawRow(job, 3, HASH, "OTHER", "NONE", null, null, "[]"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawRow(job, 3, HASH, "CREATE", "OTHER", null, null, "[]"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawRow(job, 3, "ABC", "CREATE", "NONE", null, null, "[]"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawRow(job, 3, HASH, "CREATE", "NONE", null, null, "{}"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawRow(job, 3, HASH, "CREATE", "PRODUCT_UUID", product, 0L, "[]"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRawRow(job, 3, HASH, "UPDATE", "PRODUCT_UUID", product, -1L, "[]"))
                .isInstanceOf(DataAccessException.class);
        assertRejected("UPDATE sheet_import_rows SET execution_status = 'SUCCEEDED' WHERE import_row_uuid = ?", row);
        assertRejected("UPDATE sheet_import_rows SET execution_status = 'SUCCEEDED', result_product_uuid = ?, "
                + "result_product_id = 'BAD' WHERE import_row_uuid = ?", product, row);
        UUID otherProduct = insertProduct("ROW-CONSTRAINT-OTHER");
        String otherProductId = jdbc.queryForObject(
                "SELECT product_id FROM products WHERE product_uuid = ?", String.class, otherProduct);
        assertRejected("UPDATE sheet_import_rows SET execution_status = 'SUCCEEDED', result_product_uuid = ?, "
                + "result_product_id = ? WHERE import_row_uuid = ?", product, otherProductId, row);
        assertRejected("UPDATE sheet_import_rows SET execution_status = 'FAILED', "
                + "execution_error_code = ' ', execution_error_message = 'failure' WHERE import_row_uuid = ?", row);
        assertRejected("INSERT INTO sheet_import_rows "
                + "(import_row_uuid, import_job_uuid, row_number, source_row_hash, planned_action, match_strategy, "
                + "target_product_uuid, target_product_version) VALUES (?, ?, 3, ?, 'UPDATE', 'PRODUCT_UUID', ?, 0)",
                UUID.randomUUID(), job, HASH, UUID.randomUUID());
    }

    @Test
    void directSqlCannotChangePreviewIdentityOrDeleteImportHistory() {
        UUID product = insertProduct("IMMUTABLE");
        UUID job = insertJob(1, 0);
        UUID row = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO sheet_import_rows
                    (import_row_uuid, import_job_uuid, row_number, source_row_hash, planned_action,
                     match_strategy, target_product_uuid, target_product_version, product_name)
                VALUES (?, ?, 2, ?, 'UPDATE', 'PRODUCT_UUID', ?, 0, 'Product')
                """, row, job, HASH, product);

        assertImmutable("UPDATE sheet_import_jobs SET spreadsheet_id = 'changed' WHERE import_job_uuid = ?", job,
                "sheet import job source identity is immutable");
        assertImmutable("UPDATE sheet_import_rows SET product_name = 'changed' WHERE import_row_uuid = ?", row,
                "sheet import row preview snapshot is immutable");
        assertImmutable("DELETE FROM sheet_import_rows WHERE import_row_uuid = ?", row,
                "sheet import records cannot be deleted");
        assertImmutable("DELETE FROM sheet_import_jobs WHERE import_job_uuid = ?", job,
                "sheet import records cannot be deleted");
    }

    @Test
    void jpaPersistsPreviewJsonAndOptimisticExecutionState() {
        UUID product = insertProduct("JPA");
        SheetImportJob job = jobs.saveAndFlush(SheetImportJob.previewed(
                UUID.randomUUID(), "sheet-id", "Products", "Products!A:M", HASH,
                ProductSheetMapping.ALL_HEADER_MASK, 1, 1, "local-admin"));
        SheetProductRowSnapshot snapshot = new SheetProductRowSnapshot(
                null, null, "SKU-JPA", "Product JPA", null, null, null, null,
                "not-a-number", "10.00", "TWD", "5", "https://example.com/product");
        SheetImportRow createRow = rows.saveAndFlush(SheetImportRow.create(
                UUID.randomUUID(), job.getImportJobUuid(), 2, HASH, snapshot));
        SheetImportRow invalidRow = rows.saveAndFlush(SheetImportRow.invalid(
                UUID.randomUUID(), job.getImportJobUuid(), 3, "b".repeat(64),
                SheetImportMatchStrategy.NONE, null, null, snapshot,
                List.of(new SheetValidationError("cost", "INVALID_DECIMAL", "Cost must be numeric"))));

        assertThat(job.getVersion()).isZero();
        assertThat(createRow.getExecutionStatus()).isEqualTo(SheetImportExecutionStatus.PENDING);
        assertThat(invalidRow.getExecutionStatus()).isEqualTo(SheetImportExecutionStatus.SKIPPED);
        assertThat(rows.findByImportJobUuidOrderByRowNumber(job.getImportJobUuid()))
                .extracting(SheetImportRow::getRowNumber).containsExactly(2, 3);
        assertThat(rows.findById(invalidRow.getImportRowUuid()).orElseThrow().getValidationErrors())
                .containsExactly(new SheetValidationError("cost", "INVALID_DECIMAL", "Cost must be numeric"));

        String productId = jdbc.queryForObject(
                "SELECT product_id FROM products WHERE product_uuid = ?", String.class, product);
        createRow.recordSuccess(product, productId);
        createRow = rows.saveAndFlush(createRow);
        job.startExecution();
        job.complete(1, 0, 0);
        job = jobs.saveAndFlush(job);

        assertThat(createRow.getVersion()).isEqualTo(1L);
        assertThat(job.getVersion()).isEqualTo(1L);
        assertThat(job.getStatus()).isEqualTo(SheetImportStatus.COMPLETED_WITH_ERRORS);
    }

    private UUID insertJob(int validRows, int invalidRows) {
        UUID job = UUID.randomUUID();
        insertRawJob(job, "GOOGLE_SHEETS", HASH, "PREVIEWED",
                validRows + invalidRows, validRows, invalidRows);
        return job;
    }

    private void insertRawJob(UUID job, String provider, String fingerprint, String status,
            int totalRows, int validRows, int invalidRows) {
        insertRawJob(job, provider, fingerprint, status, totalRows, validRows, invalidRows,
                ProductSheetMapping.ALL_HEADER_MASK);
    }

    private void insertRawJob(UUID job, String provider, String fingerprint, String status,
            int totalRows, int validRows, int invalidRows, int headerPresenceMask) {
        jdbc.update("""
                INSERT INTO sheet_import_jobs
                    (import_job_uuid, provider, spreadsheet_id, sheet_name, source_range,
                     source_fingerprint, header_presence_mask, status, total_rows, valid_rows, invalid_rows, created_by)
                VALUES (?, ?, 'sheet-id', 'Products', 'Products!A:M', ?, ?, ?, ?, ?, ?, 'local-admin')
                """, job, provider, fingerprint, headerPresenceMask, status, totalRows, validRows, invalidRows);
    }

    private void insertRawRow(UUID job, int rowNumber, String hash, String action, String strategy,
            UUID targetProductUuid, Long targetProductVersion, String validationErrors) {
        jdbc.update("""
                INSERT INTO sheet_import_rows
                    (import_row_uuid, import_job_uuid, row_number, source_row_hash, planned_action,
                     match_strategy, target_product_uuid, target_product_version, validation_errors)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), job, rowNumber, hash, action, strategy,
                targetProductUuid, targetProductVersion, validationErrors);
    }

    private UUID insertProduct(String suffix) {
        UUID uuid = UUID.randomUUID();
        Long sequence = jdbc.queryForObject("SELECT nextval('product_id_seq')", Long.class);
        jdbc.update("INSERT INTO products "
                        + "(product_uuid, product_id, sku, product_name, lifecycle_status, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                uuid, "PROD-%08d".formatted(sequence), "SKU-" + suffix, "Product " + suffix);
        return uuid;
    }

    private boolean tableExists(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?", Integer.class, name) == 1;
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private void assertImmutable(String sql, Object id, String message) {
        assertThatThrownBy(() -> jdbc.update(sql, id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(message);
    }
}
