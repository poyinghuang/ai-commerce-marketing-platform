package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.PatchField;
import com.aicommerce.platform.product.application.PatchProductCommand;
import com.aicommerce.platform.product.application.ProductPreconditionFailedException;
import com.aicommerce.platform.product.application.ProductQueryService;
import com.aicommerce.platform.product.application.ProductSearchCriteria;
import com.aicommerce.platform.product.application.ProductIdGenerator;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PersistenceFoundationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ProductIdGenerator productIdGenerator;

    @Autowired
    ProductCommandService productService;

    @Autowired
    ProductJpaRepository productRepository;

    @Autowired
    ProductQueryService productQueryService;

    @Autowired
    AuditOperationContextFactory contextFactory;

    @Autowired
    AuditWriter auditWriter;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Environment environment;

    @Test
    void migrationsRunFromEmptyDatabaseAndHibernateValidatesTheSchema() {
        List<String> appliedVersions = List.of(flyway.info().applied()).stream()
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertThat(appliedVersions).containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(tableExists("products")).isTrue();
        assertThat(tableExists("audit_logs")).isTrue();
        assertThat(tableExists("audit_log_changes")).isTrue();
        assertThat(List.of("product_knowledge", "creative_plans", "campaign_plans", "campaign_products", "assets"))
                .allMatch(this::tableExists);
        assertThat(List.of("quality_scores", "quality_score_blockers", "workflow_status"))
                .allMatch(this::tableExists);
        assertThat(List.of("sheet_import_jobs", "sheet_import_rows")).allMatch(this::tableExists);
        assertThat(List.of("product_storage_folders", "product_storage_subfolders"))
                .allMatch(this::tableExists);
    }

    @Test
    void flywayCleanIsExplicitlyDisabled() {
        assertThat(environment.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
        assertThatThrownBy(flyway::clean)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void productIdSequenceIsNoCycleAndConcurrentAllocationsAreUnique() throws Exception {
        Map<String, Object> sequence = jdbcTemplate.queryForMap("""
                SELECT cycle, min_value, max_value, increment_by
                FROM pg_sequences
                WHERE schemaname = 'public' AND sequencename = 'product_id_seq'
                """);
        assertThat(sequence.get("cycle")).isEqualTo(false);
        assertThat(((Number) sequence.get("min_value")).longValue()).isEqualTo(1L);
        assertThat(((Number) sequence.get("max_value")).longValue()).isEqualTo(99_999_999L);
        assertThat(((Number) sequence.get("increment_by")).longValue()).isEqualTo(1L);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> allocations = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                allocations.add(productIdGenerator::nextId);
            }
            List<String> productIds = new ArrayList<>();
            for (Future<String> future : executor.invokeAll(allocations)) {
                productIds.add(future.get());
            }

            assertThat(productIds).allMatch(id -> id.matches("PROD-[0-9]{8}"));
            assertThat(new HashSet<>(productIds)).hasSameSizeAs(productIds);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void productPersistsUuidTimestampsVersionArchiveAndAllowsDuplicateSku() {
        Product first = createProduct("DUPLICATE-SKU", "request-product-1");
        Product second = createProduct("DUPLICATE-SKU", "request-product-2");
        Product persistedFirst = productRepository.findById(first.getProductUuid()).orElseThrow();

        assertThat(first.getProductUuid()).isNotEqualTo(second.getProductUuid());
        assertThat(first.getProductId()).matches("PROD-[0-9]{8}");
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getUpdatedAt()).isNotNull();
        assertThat(persistedFirst.getCreatedAt()).isNotNull();
        assertThat(persistedFirst.getUpdatedAt()).isNotNull();
        assertThat(persistedFirst.getVersion()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE sku = 'DUPLICATE-SKU'", Integer.class)).isEqualTo(2);

        productService.archive(first.getProductUuid(), first.getVersion(), "request-archive-1");
        Product archived = productRepository.findById(first.getProductUuid()).orElseThrow();
        assertThat(archived.getLifecycleStatus()).isEqualTo(ProductLifecycleStatus.ARCHIVED);
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getVersion()).isEqualTo(1L);
    }

    @Test
    void productMasterCreatePatchNoOpAndAuditOnlyRecordActualChanges() {
        Product product = productService.create(new CreateProductCommand(
                "MASTER-SKU",
                "Master Product",
                "Brand A",
                "Category A",
                "Subcategory A",
                "Original description",
                new BigDecimal("10.0000"),
                new BigDecimal("15.5000"),
                "TWD",
                12L,
                "https://example.com/master"), "request-master-create");
        int createAuditCount = auditCount(product.getProductUuid());

        Product updated = productService.patch(
                product.getProductUuid(),
                product.getVersion(),
                new PatchProductCommand(
                        PatchField.absent(),
                        PatchField.present("Master Product Updated"),
                        PatchField.absent(),
                        PatchField.absent(),
                        PatchField.absent(),
                        PatchField.absent(),
                        PatchField.absent(),
                        PatchField.present(new BigDecimal("14.0000")),
                        PatchField.absent(),
                        PatchField.absent(),
                        PatchField.absent()),
                "request-master-update");

        assertThat(updated.getProductName()).isEqualTo("Master Product Updated");
        assertThat(updated.getSalePrice()).isEqualByComparingTo("14.0000");
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(auditCount(product.getProductUuid())).isEqualTo(createAuditCount + 1);
        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT c.field_name
                        FROM audit_log_changes c
                        JOIN audit_logs l ON l.audit_uuid = c.audit_uuid
                        WHERE l.product_uuid = ? AND l.action = 'UPDATE'
                        ORDER BY c.change_order
                        """,
                        product.getProductUuid()))
                .extracting(row -> row.get("field_name"))
                .containsExactly("product_name", "sale_price");

        Product noOp = productService.patch(
                product.getProductUuid(),
                updated.getVersion(),
                absentPatch(),
                "request-master-noop");
        assertThat(noOp.getVersion()).isEqualTo(updated.getVersion());
        assertThat(auditCount(product.getProductUuid())).isEqualTo(createAuditCount + 1);
    }

    @Test
    void archiveAndRestoreAreIdempotentWithCurrentVersionAndRejectStaleVersion() {
        Product product = createProduct("IDEMPOTENT-SKU", "request-idempotent-create");
        int initialAudits = auditCount(product.getProductUuid());

        Product archived = productService.archive(
                product.getProductUuid(), product.getVersion(), "request-idempotent-archive");
        long archivedVersion = archived.getVersion();
        Product archivedAgain = productService.archive(
                product.getProductUuid(), archivedVersion, "request-idempotent-archive-again");
        assertThat(archivedAgain.getVersion()).isEqualTo(archivedVersion);
        assertThat(auditCount(product.getProductUuid())).isEqualTo(initialAudits + 3);
        assertThat(productQueryService.search(
                        new ProductSearchCriteria(ProductLifecycleStatus.ACTIVE, null, null, null, null),
                        PageRequest.of(0, 20)))
                .extracting(Product::getProductUuid)
                .doesNotContain(product.getProductUuid());
        assertThat(productQueryService.search(
                        new ProductSearchCriteria(ProductLifecycleStatus.ARCHIVED, null, null, null, null),
                        PageRequest.of(0, 20)))
                .extracting(Product::getProductUuid)
                .contains(product.getProductUuid());
        assertThat(productQueryService.findByUuid(product.getProductUuid()).getLifecycleStatus())
                .isEqualTo(ProductLifecycleStatus.ARCHIVED);
        assertThatThrownBy(() -> productService.archive(
                product.getProductUuid(), 0L, "request-idempotent-stale"))
                .isInstanceOf(ProductPreconditionFailedException.class);

        Product restored = productService.restore(
                product.getProductUuid(), archivedVersion, "request-idempotent-restore");
        long restoredVersion = restored.getVersion();
        Product restoredAgain = productService.restore(
                product.getProductUuid(), restoredVersion, "request-idempotent-restore-again");
        assertThat(restoredAgain.getVersion()).isEqualTo(restoredVersion);
        assertThat(auditCount(product.getProductUuid())).isEqualTo(initialAudits + 6);
    }

    @Test
    void productSearchSupportsFiltersKeywordSortAndStablePagination() {
        Product alpha = productService.create(new CreateProductCommand(
                "SEARCH-ALPHA", "Search Alpha Serum", "Search Brand", "Search Category", null,
                null, null, new BigDecimal("20.0000"), "TWD", 3L, null), "request-search-alpha");
        productService.create(new CreateProductCommand(
                "SEARCH-BETA", "Search Beta Cream", "Search Brand", "Other Category", null,
                null, null, new BigDecimal("10.0000"), "TWD", 4L, null), "request-search-beta");

        var result = productQueryService.search(
                new ProductSearchCriteria(
                        ProductLifecycleStatus.ACTIVE, "search category", "serum", "search-alpha", null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "productName")
                        .and(Sort.by("productUuid").ascending())));

        assertThat(result.getContent()).extracting(Product::getProductUuid).containsExactly(alpha.getProductUuid());
    }

    @Test
    void databaseRejectsInvalidProductMasterValues() {
        Product product = createProduct("MASTER-CONSTRAINT", "request-master-constraint");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET cost = -1, currency = 'TWD' WHERE product_uuid = ?",
                product.getProductUuid())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET sale_price = -1, currency = 'TWD' WHERE product_uuid = ?",
                product.getProductUuid())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET stock = -1 WHERE product_uuid = ?",
                product.getProductUuid())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET currency = 'twd' WHERE product_uuid = ?",
                product.getProductUuid())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET cost = 1, currency = NULL WHERE product_uuid = ?",
                product.getProductUuid())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void optimisticLockRejectsAStaleConcurrentUpdate() {
        Product product = createProduct("LOCK-ORIGINAL", "request-lock");
        EntityManager firstManager = entityManagerFactory.createEntityManager();
        EntityManager secondManager = entityManagerFactory.createEntityManager();
        try {
            firstManager.getTransaction().begin();
            secondManager.getTransaction().begin();
            Product firstCopy = firstManager.find(Product.class, product.getProductUuid());
            Product staleCopy = secondManager.find(Product.class, product.getProductUuid());

            firstCopy.updateMasterData(
                    "LOCK-WINNER", firstCopy.getProductName(), firstCopy.getBrand(), firstCopy.getCategory(),
                    firstCopy.getSubcategory(), firstCopy.getShortDescription(), firstCopy.getCost(),
                    firstCopy.getSalePrice(), firstCopy.getCurrency(), firstCopy.getStock(), firstCopy.getProductUrl());
            firstManager.getTransaction().commit();
            staleCopy.updateMasterData(
                    "LOCK-STALE", staleCopy.getProductName(), staleCopy.getBrand(), staleCopy.getCategory(),
                    staleCopy.getSubcategory(), staleCopy.getShortDescription(), staleCopy.getCost(),
                    staleCopy.getSalePrice(), staleCopy.getCurrency(), staleCopy.getStock(), staleCopy.getProductUrl());

            assertThatThrownBy(() -> secondManager.getTransaction().commit())
                    .isInstanceOf(jakarta.persistence.RollbackException.class)
                    .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
        } finally {
            if (firstManager.getTransaction().isActive()) {
                firstManager.getTransaction().rollback();
            }
            if (secondManager.getTransaction().isActive()) {
                secondManager.getTransaction().rollback();
            }
            firstManager.close();
            secondManager.close();
        }

        assertThat(productRepository.findById(product.getProductUuid()).orElseThrow().getSku())
                .isEqualTo("LOCK-WINNER");
    }

    @Test
    void databaseTriggerRejectsProductIdentityChangesThroughDirectSql() {
        Product product = createProduct("IMMUTABLE", "request-immutable");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET product_uuid = ? WHERE product_uuid = ?",
                UUID.randomUUID(), product.getProductUuid()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_uuid is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE products SET product_id = ? WHERE product_uuid = ?",
                productIdGenerator.nextId(), product.getProductUuid()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("product_id is immutable");
    }

    @Test
    void databaseConstraintsRejectInvalidProductAndAuditChangeRowsThroughDirectSql() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO products
                    (product_uuid, product_id, lifecycle_status, created_at, updated_at, version)
                VALUES (?, 'INVALID', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO products
                    (product_uuid, product_id, lifecycle_status, archived_at, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, UUID.randomUUID(), productIdGenerator.nextId()))
                .isInstanceOf(DataAccessException.class);

        Product product = createProduct("AUDIT-CONSTRAINT", "request-constraint");
        UUID auditUuid = jdbcTemplate.queryForObject(
                "SELECT audit_uuid FROM audit_logs WHERE product_uuid = ? ORDER BY occurred_at DESC LIMIT 1",
                UUID.class,
                product.getProductUuid());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO audit_log_changes
                    (audit_change_uuid, audit_uuid, field_name, value_type, change_order)
                VALUES (?, ?, 'sku', 'STRING', -1)
                """, UUID.randomUUID(), auditUuid))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO audit_log_changes
                    (audit_change_uuid, audit_uuid, field_name, value_type, change_order)
                VALUES (?, ?, 'sku', 'STRING', NULL)
                """, UUID.randomUUID(), auditUuid))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsInvalidAuditActionThroughDirectSql() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO audit_logs
                    (audit_uuid, operation_uuid, request_id, actor_type, actor_id, source,
                     action, entity_type, entity_uuid, occurred_at)
                VALUES (?, ?, 'request-invalid-action', 'SYSTEM', 'constraint-test', 'SYSTEM',
                        'INVALID', 'PRODUCT', ?, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsInvalidAuditValueTypeThroughDirectSql() {
        Product product = createProduct("INVALID-VALUE-TYPE", "request-invalid-value-type");
        UUID auditUuid = jdbcTemplate.queryForObject(
                "SELECT audit_uuid FROM audit_logs WHERE product_uuid = ? ORDER BY occurred_at DESC LIMIT 1",
                UUID.class,
                product.getProductUuid());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO audit_log_changes
                    (audit_change_uuid, audit_uuid, field_name, value_type, change_order)
                VALUES (?, ?, 'sku', 'INVALID', 100)
                """, UUID.randomUUID(), auditUuid))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseTriggersRejectUpdatesAndDeletesOnBothAuditTablesThroughDirectSql() {
        Product product = createProduct("AUDIT-TRIGGER", "request-trigger");
        UUID auditUuid = jdbcTemplate.queryForObject(
                "SELECT audit_uuid FROM audit_logs WHERE product_uuid = ? ORDER BY occurred_at DESC LIMIT 1",
                UUID.class,
                product.getProductUuid());
        UUID changeUuid = jdbcTemplate.queryForObject(
                "SELECT audit_change_uuid FROM audit_log_changes WHERE audit_uuid = ? ORDER BY change_order LIMIT 1",
                UUID.class,
                auditUuid);

        assertAppendOnlyFailure("UPDATE audit_logs SET action = 'UPDATE' WHERE audit_uuid = ?", auditUuid);
        assertAppendOnlyFailure("DELETE FROM audit_logs WHERE audit_uuid = ?", auditUuid);
        assertAppendOnlyFailure(
                "UPDATE audit_log_changes SET field_name = 'changed' WHERE audit_change_uuid = ?", changeUuid);
        assertAppendOnlyFailure("DELETE FROM audit_log_changes WHERE audit_change_uuid = ?", changeUuid);
    }

    @Test
    void auditWriterRedactsThenTruncatesAndAllowsRepeatedFieldNamesInOrder() {
        Product product = createProduct("AUDIT-VALUES", "request-values-product");
        UUID auditUuid = UUID.randomUUID();
        AuditOperationContext context = contextFactory.forCurrentActor("request-values");
        String longValue = "😀".repeat(4_100);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> auditWriter.append(new AuditEvent(
                auditUuid,
                context,
                AuditAction.UPDATE,
                "PRODUCT",
                product.getProductUuid(),
                product.getProductUuid(),
                Instant.now(),
                List.of(
                        new AuditChange("password", "old-secret", "x".repeat(5_000), AuditValueType.STRING, 0),
                        new AuditChange("description", null, longValue, AuditValueType.STRING, 1),
                        new AuditChange("description", "before", "after", AuditValueType.STRING, 2),
                        new AuditChange("budget", "10.0000", "20.0000", AuditValueType.DECIMAL, 3),
                        new AuditChange("priority", "1", "2", AuditValueType.INTEGER, 4),
                        new AuditChange("startDate", "2026-08-01", "2026-08-07", AuditValueType.DATE, 5)))));

        List<Map<String, Object>> values = jdbcTemplate.queryForList("""
                SELECT field_name, old_value, new_value, value_type, change_order
                FROM audit_log_changes
                WHERE audit_uuid = ?
                ORDER BY change_order
                """, auditUuid);
        assertThat(values).hasSize(6);
        assertThat(values.get(0).get("old_value")).isEqualTo("[REDACTED]");
        assertThat(values.get(0).get("new_value")).isEqualTo("[REDACTED]");
        String storedLongValue = (String) values.get(1).get("new_value");
        assertThat(storedLongValue).endsWith("[TRUNCATED]");
        assertThat(storedLongValue.codePointCount(0, storedLongValue.length())).isEqualTo(4_096);
        assertThat(values.stream().filter(row -> row.get("field_name").equals("description"))).hasSize(2);
        assertThat(values).extracting(row -> row.get("value_type"))
                .containsExactly("STRING", "STRING", "STRING", "DECIMAL", "INTEGER", "DATE");
    }

    @Test
    void systemOperationGeneratesOneNonEmptyRequestIdSharedAcrossItsAuditEvents() {
        Product product = createProduct("SYSTEM-CONTEXT", "request-system-product");
        AuditOperationContext context = contextFactory.forSystem("catalog-importer");
        UUID firstAuditUuid = UUID.randomUUID();
        UUID secondAuditUuid = UUID.randomUUID();

        assertThat(context.requestId()).isNotBlank();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auditWriter.append(eventWithoutChanges(firstAuditUuid, product.getProductUuid(), context));
            auditWriter.append(eventWithoutChanges(secondAuditUuid, product.getProductUuid(), context));
        });

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT operation_uuid, request_id, actor_type, actor_id
                FROM audit_logs
                WHERE audit_uuid IN (?, ?)
                """, firstAuditUuid, secondAuditUuid);
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("operation_uuid")).containsOnly(context.operationUuid());
        assertThat(rows).extracting(row -> row.get("request_id")).containsOnly(context.requestId());
        assertThat(rows).extracting(row -> row.get("actor_type")).containsOnly("SYSTEM");
        assertThat(rows).extracting(row -> row.get("actor_id")).containsOnly("catalog-importer");
    }

    @Test
    void productAndAuditRollbackTogetherWhenAnAuditConstraintFails() {
        UUID productUuid = UUID.randomUUID();
        UUID auditUuid = UUID.randomUUID();
        String productId = productIdGenerator.nextId();
        AuditOperationContext context = contextFactory.forSystem("rollback-test");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    INSERT INTO products
                        (product_uuid, product_id, lifecycle_status, created_at, updated_at, version)
                    VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """, productUuid, productId);
            auditWriter.append(new AuditEvent(
                    auditUuid,
                    context,
                    AuditAction.CREATE,
                    "PRODUCT",
                    productUuid,
                    productUuid,
                    Instant.now(),
                    List.of(
                            new AuditChange("sku", null, "one", AuditValueType.STRING, 0),
                            new AuditChange("sku", null, "two", AuditValueType.STRING, 0))));
        })).isInstanceOf(RuntimeException.class);

        assertThat(countByUuid("products", "product_uuid", productUuid)).isZero();
        assertThat(countByUuid("audit_logs", "audit_uuid", auditUuid)).isZero();
        assertThat(countByUuid("audit_log_changes", "audit_uuid", auditUuid)).isZero();
    }

    private AuditEvent eventWithoutChanges(UUID auditUuid, UUID productUuid, AuditOperationContext context) {
        return new AuditEvent(
                auditUuid,
                context,
                AuditAction.UPDATE,
                "PRODUCT",
                productUuid,
                productUuid,
                Instant.now(),
                List.of());
    }

    private Product createProduct(String sku, String requestId) {
        return productService.create(new CreateProductCommand(
                sku,
                "Product " + sku,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null), requestId);
    }

    private PatchProductCommand absentPatch() {
        return new PatchProductCommand(
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent());
    }

    private int auditCount(UUID productUuid) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE product_uuid = ?", Integer.class, productUuid);
    }

    private void assertAppendOnlyFailure(String sql, UUID uuid) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, uuid))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private int countByUuid(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }
}
