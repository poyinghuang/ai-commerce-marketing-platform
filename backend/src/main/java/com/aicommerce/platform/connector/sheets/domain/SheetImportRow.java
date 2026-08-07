package com.aicommerce.platform.connector.sheets.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.MutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sheet_import_rows")
public class SheetImportRow extends MutableEntity {

    private static final int MAX_DATA_ROW_NUMBER = 1_001;

    @Id
    @Column(name = "import_row_uuid", nullable = false, updatable = false)
    private UUID importRowUuid;

    @Column(name = "import_job_uuid", nullable = false, updatable = false)
    private UUID importJobUuid;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_row_hash", nullable = false, updatable = false, columnDefinition = "char(64)")
    private String sourceRowHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_action", nullable = false, updatable = false, length = 16)
    private SheetImportPlannedAction plannedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_strategy", nullable = false, updatable = false, length = 16)
    private SheetImportMatchStrategy matchStrategy;

    @Column(name = "target_product_uuid", updatable = false)
    private UUID targetProductUuid;

    @Column(name = "target_product_version", updatable = false)
    private Long targetProductVersion;

    @Column(name = "source_product_uuid", updatable = false, length = 128)
    private String sourceProductUuid;
    @Column(name = "source_product_id", updatable = false, length = 128)
    private String sourceProductId;
    @Column(name = "sku", updatable = false, length = 512)
    private String sku;
    @Column(name = "product_name", updatable = false, length = 1024)
    private String productName;
    @Column(name = "brand", updatable = false, length = 512)
    private String brand;
    @Column(name = "category", updatable = false, length = 512)
    private String category;
    @Column(name = "subcategory", updatable = false, length = 512)
    private String subcategory;
    @Column(name = "short_description", updatable = false, length = 4096)
    private String shortDescription;
    @Column(name = "source_cost", updatable = false, length = 128)
    private String sourceCost;
    @Column(name = "source_sale_price", updatable = false, length = 128)
    private String sourceSalePrice;
    @Column(name = "currency", updatable = false, length = 32)
    private String currency;
    @Column(name = "source_stock", updatable = false, length = 128)
    private String sourceStock;
    @Column(name = "product_url", updatable = false, length = 4096)
    private String productUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", nullable = false, updatable = false, columnDefinition = "jsonb")
    private List<SheetValidationError> validationErrors;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 16)
    private SheetImportExecutionStatus executionStatus;

    @Column(name = "result_product_uuid")
    private UUID resultProductUuid;

    @Column(name = "result_product_id", length = 13)
    private String resultProductId;

    @Column(name = "execution_error_code", length = 64)
    private String executionErrorCode;

    @Column(name = "execution_error_message", length = 1000)
    private String executionErrorMessage;

    protected SheetImportRow() {
    }

    private SheetImportRow(UUID importRowUuid, UUID importJobUuid, int rowNumber, String sourceRowHash,
            SheetImportPlannedAction plannedAction, SheetImportMatchStrategy matchStrategy,
            UUID targetProductUuid, Long targetProductVersion, SheetProductRowSnapshot snapshot,
            List<SheetValidationError> validationErrors) {
        this.importRowUuid = Objects.requireNonNull(importRowUuid, "importRowUuid is required");
        this.importJobUuid = Objects.requireNonNull(importJobUuid, "importJobUuid is required");
        if (rowNumber < 2 || rowNumber > MAX_DATA_ROW_NUMBER) {
            throw new IllegalArgumentException("rowNumber must be between 2 and " + MAX_DATA_ROW_NUMBER);
        }
        this.rowNumber = rowNumber;
        this.sourceRowHash = requireSha256(sourceRowHash, "sourceRowHash");
        this.plannedAction = Objects.requireNonNull(plannedAction, "plannedAction is required");
        this.matchStrategy = Objects.requireNonNull(matchStrategy, "matchStrategy is required");
        this.targetProductUuid = targetProductUuid;
        this.targetProductVersion = targetProductVersion;
        applySnapshot(Objects.requireNonNull(snapshot, "snapshot is required"));
        this.validationErrors = List.copyOf(Objects.requireNonNull(validationErrors, "validationErrors is required"));
        validatePlan();
        this.executionStatus = plannedAction == SheetImportPlannedAction.INVALID
                ? SheetImportExecutionStatus.SKIPPED
                : SheetImportExecutionStatus.PENDING;
    }

    public static SheetImportRow create(UUID rowUuid, UUID jobUuid, int rowNumber, String rowHash,
            SheetProductRowSnapshot snapshot) {
        return new SheetImportRow(rowUuid, jobUuid, rowNumber, rowHash,
                SheetImportPlannedAction.CREATE, SheetImportMatchStrategy.NONE,
                null, null, snapshot, List.of());
    }

    public static SheetImportRow update(UUID rowUuid, UUID jobUuid, int rowNumber, String rowHash,
            SheetImportMatchStrategy strategy, UUID targetProductUuid, long targetProductVersion,
            SheetProductRowSnapshot snapshot) {
        return new SheetImportRow(rowUuid, jobUuid, rowNumber, rowHash,
                SheetImportPlannedAction.UPDATE, strategy, targetProductUuid, targetProductVersion,
                snapshot, List.of());
    }

    public static SheetImportRow invalid(UUID rowUuid, UUID jobUuid, int rowNumber, String rowHash,
            SheetImportMatchStrategy strategy, UUID targetProductUuid, Long targetProductVersion,
            SheetProductRowSnapshot snapshot, List<SheetValidationError> validationErrors) {
        return new SheetImportRow(rowUuid, jobUuid, rowNumber, rowHash,
                SheetImportPlannedAction.INVALID, strategy, targetProductUuid, targetProductVersion,
                snapshot, validationErrors);
    }

    public void recordSuccess(UUID productUuid, String productId) {
        requirePending();
        UUID validatedProductUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        String validatedProductId = requireText(productId, "productId", 13);
        if (!validatedProductId.matches("PROD-[0-9]{8}")) {
            throw new IllegalArgumentException("productId must match PROD-00000000");
        }
        this.resultProductUuid = validatedProductUuid;
        this.resultProductId = validatedProductId;
        this.executionStatus = SheetImportExecutionStatus.SUCCEEDED;
    }

    public void recordFailure(String code, String message) {
        requirePending();
        this.executionErrorCode = requireText(code, "code", 64);
        this.executionErrorMessage = requireText(message, "message", 1000);
        this.executionStatus = SheetImportExecutionStatus.FAILED;
    }

    private void requirePending() {
        if (executionStatus != SheetImportExecutionStatus.PENDING) {
            throw new IllegalStateException("only a pending row can record an execution result");
        }
    }

    private void validatePlan() {
        if (targetProductVersion != null && targetProductVersion < 0) {
            throw new IllegalArgumentException("targetProductVersion must be non-negative");
        }
        switch (plannedAction) {
            case CREATE -> {
                if (matchStrategy != SheetImportMatchStrategy.NONE
                        || targetProductUuid != null || targetProductVersion != null
                        || !validationErrors.isEmpty()) {
                    throw new IllegalArgumentException("CREATE row cannot have a target or validation errors");
                }
            }
            case UPDATE -> {
                if (matchStrategy == SheetImportMatchStrategy.NONE
                        || targetProductUuid == null || targetProductVersion == null
                        || !validationErrors.isEmpty()) {
                    throw new IllegalArgumentException("UPDATE row requires a matched target without errors");
                }
            }
            case INVALID -> {
                if (validationErrors.isEmpty()) {
                    throw new IllegalArgumentException("INVALID row requires validation errors");
                }
            }
        }
    }

    private void applySnapshot(SheetProductRowSnapshot snapshot) {
        sourceProductUuid = bounded(snapshot.productUuid(), "productUuid", 128);
        sourceProductId = bounded(snapshot.productId(), "productId", 128);
        sku = bounded(snapshot.sku(), "sku", 512);
        productName = bounded(snapshot.productName(), "productName", 1024);
        brand = bounded(snapshot.brand(), "brand", 512);
        category = bounded(snapshot.category(), "category", 512);
        subcategory = bounded(snapshot.subcategory(), "subcategory", 512);
        shortDescription = bounded(snapshot.shortDescription(), "shortDescription", 4096);
        sourceCost = bounded(snapshot.cost(), "cost", 128);
        sourceSalePrice = bounded(snapshot.salePrice(), "salePrice", 128);
        currency = bounded(snapshot.currency(), "currency", 32);
        sourceStock = bounded(snapshot.stock(), "stock", 128);
        productUrl = bounded(snapshot.productUrl(), "productUrl", 4096);
    }

    private static String bounded(String value, String name, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " source value exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String requireSha256(String value, String name) {
        String normalized = requireText(value, name, 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 value");
        }
        return normalized;
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID getImportRowUuid() { return importRowUuid; }
    public UUID getImportJobUuid() { return importJobUuid; }
    public int getRowNumber() { return rowNumber; }
    public String getSourceRowHash() { return sourceRowHash; }
    public SheetImportPlannedAction getPlannedAction() { return plannedAction; }
    public SheetImportMatchStrategy getMatchStrategy() { return matchStrategy; }
    public UUID getTargetProductUuid() { return targetProductUuid; }
    public Long getTargetProductVersion() { return targetProductVersion; }
    public SheetProductRowSnapshot getSnapshot() {
        return new SheetProductRowSnapshot(sourceProductUuid, sourceProductId, sku, productName, brand, category,
                subcategory, shortDescription, sourceCost, sourceSalePrice, currency, sourceStock, productUrl);
    }
    public List<SheetValidationError> getValidationErrors() { return List.copyOf(validationErrors); }
    public SheetImportExecutionStatus getExecutionStatus() { return executionStatus; }
    public UUID getResultProductUuid() { return resultProductUuid; }
    public String getResultProductId() { return resultProductId; }
    public String getExecutionErrorCode() { return executionErrorCode; }
    public String getExecutionErrorMessage() { return executionErrorMessage; }
}
