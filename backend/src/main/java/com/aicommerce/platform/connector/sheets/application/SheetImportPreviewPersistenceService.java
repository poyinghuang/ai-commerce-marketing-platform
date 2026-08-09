package com.aicommerce.platform.connector.sheets.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.domain.SheetImportMatchStrategy;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import com.aicommerce.platform.connector.sheets.domain.SheetProductRowSnapshot;
import com.aicommerce.platform.connector.sheets.domain.SheetValidationError;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SheetImportPreviewPersistenceService {
    private static final String ENTITY_TYPE = "SHEET_IMPORT_JOB";
    private static final Map<String, Integer> SOURCE_LIMITS = Map.ofEntries(
            Map.entry("product_uuid", 128), Map.entry("product_id", 128), Map.entry("sku", 512),
            Map.entry("product_name", 1024), Map.entry("brand", 512), Map.entry("category", 512),
            Map.entry("subcategory", 512), Map.entry("short_description", 4096), Map.entry("cost", 128),
            Map.entry("sale_price", 128), Map.entry("currency", 32), Map.entry("stock", 128),
            Map.entry("product_url", 4096));

    private final SheetImportJobJpaRepository jobs;
    private final SheetImportRowJpaRepository rows;
    private final ProductJpaRepository products;
    private final AuditWriter audit;
    private final Clock clock;

    public SheetImportPreviewPersistenceService(SheetImportJobJpaRepository jobs,
            SheetImportRowJpaRepository rows, ProductJpaRepository products, AuditWriter audit, Clock clock) {
        this.jobs = jobs;
        this.rows = rows;
        this.products = products;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public SheetImportView persist(SheetSource source, SheetValuesSnapshot snapshot,
            AuditOperationContext context) {
        List<List<String>> values = trimTrailingEmptyRows(normalize(snapshot.values()));
        if (values.isEmpty()) throw validation("SHEET_EMPTY", "spreadsheetId", "The Sheet is empty");
        List<String> headers = trimTrailing(values.getFirst());
        validateHeaders(headers);
        int headerMask;
        try {
            headerMask = ProductSheetMapping.presenceMask(headers);
        } catch (IllegalArgumentException exception) {
            throw validation("INVALID_SHEET_HEADER", "headers", exception.getMessage());
        }
        Map<String, Integer> indexes = indexes(headers);
        List<IndexedRow> data = new ArrayList<>();
        List<List<String>> fingerprintRows = new ArrayList<>();
        fingerprintRows.add(headers);
        for (int index = 1; index < values.size(); index++) {
            List<String> original = values.get(index);
            List<String> row = canonicalRow(original, headers.size());
            fingerprintRows.add(row);
            if (original.size() > headers.size()
                    && original.subList(headers.size(), original.size()).stream().anyMatch(v -> !v.isBlank())) {
                throw validation("INVALID_SHEET_HEADER", "headers", "A row contains values outside declared headers");
            }
            if (row.stream().allMatch(String::isBlank)) continue;
            if (index + 1 > ProductSheetMapping.MAX_DATA_ROWS + 1) {
                throw validation("SHEET_ROW_LIMIT_EXCEEDED", "rows", "A preview cannot exceed Sheet row 1001");
            }
            data.add(new IndexedRow(index + 1, row));
        }
        if (data.size() > ProductSheetMapping.MAX_DATA_ROWS) {
            throw validation("SHEET_ROW_LIMIT_EXCEEDED", "rows", "A preview cannot exceed 1000 data rows");
        }
        if (data.isEmpty()) throw validation("SHEET_EMPTY", "spreadsheetId", "The Sheet has no data rows");

        Set<UUID> matched = new HashSet<>();
        List<SheetImportRow> previewRows = new ArrayList<>();
        UUID jobUuid = UUID.randomUUID();
        for (IndexedRow indexed : data) {
            previewRows.add(analyze(jobUuid, indexed, indexes, headerMask, matched));
        }
        int invalid = (int) previewRows.stream().filter(row -> !row.getValidationErrors().isEmpty()).count();
        int valid = previewRows.size() - invalid;
        String fingerprint = SheetSnapshotFingerprint.fingerprint(fingerprintRows);
        SheetImportJob job = SheetImportJob.previewed(jobUuid, source.spreadsheetId(), source.sheetName(),
                source.range(), fingerprint, headerMask, valid, invalid, context.actor().id());
        job = jobs.saveAndFlush(job);
        rows.saveAll(previewRows);
        rows.flush();
        appendAudit(job, context);
        return SheetImportView.from(job, rows.findByImportJobUuidOrderByRowNumber(jobUuid));
    }

    private SheetImportRow analyze(UUID jobUuid, IndexedRow indexed, Map<String, Integer> indexes,
            int headerMask, Set<UUID> matched) {
        List<SheetValidationError> errors = new ArrayList<>();
        SheetProductRowSnapshot source = source(indexed.values(), indexes, errors);
        Product target = null;
        SheetImportMatchStrategy strategy = SheetImportMatchStrategy.NONE;
        String uuidValue = blankToNull(source.productUuid());
        String productId = blankToNull(source.productId());
        if (uuidValue != null) {
            strategy = SheetImportMatchStrategy.PRODUCT_UUID;
            UUID uuid = parseUuid(uuidValue, errors);
            if (uuid != null) {
                target = products.findById(uuid).orElse(null);
                if (target == null) error(errors, "product_uuid", "PRODUCT_NOT_FOUND", "Product UUID was not found");
                else if (productId != null && !target.getProductId().equals(productId))
                    error(errors, "product_id", "PRODUCT_ID_MISMATCH", "Product ID does not match Product UUID");
            }
        } else if (productId != null) {
            strategy = SheetImportMatchStrategy.PRODUCT_ID;
            if (!productId.matches("PROD-[0-9]{8}")) {
                error(errors, "product_id", "INVALID_PRODUCT_ID", "Product ID has an invalid format");
            } else {
                target = products.findByProductId(productId).orElse(null);
                if (target == null) error(errors, "product_id", "PRODUCT_NOT_FOUND", "Product ID was not found");
            }
        }
        if (target != null) {
            if (!matched.add(target.getProductUuid()))
                error(errors, "product_uuid", "DUPLICATE_PRODUCT_TARGET", "Product appears more than once in this preview");
            if (target.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED)
                error(errors, "product_uuid", "PRODUCT_ARCHIVED", "Archived Product cannot be imported");
        }
        validateProduct(source, target, headerMask, errors);
        String hash = SheetSnapshotFingerprint.fingerprint(List.of(indexed.values()));
        if (!errors.isEmpty()) {
            return SheetImportRow.invalid(UUID.randomUUID(), jobUuid, indexed.rowNumber(), hash, strategy,
                    target == null ? null : target.getProductUuid(), target == null ? null : target.getVersion(),
                    source, errors);
        }
        return target == null
                ? SheetImportRow.create(UUID.randomUUID(), jobUuid, indexed.rowNumber(), hash, source)
                : SheetImportRow.update(UUID.randomUUID(), jobUuid, indexed.rowNumber(), hash, strategy,
                        target.getProductUuid(), target.getVersion(), source);
    }

    private SheetProductRowSnapshot source(List<String> row, Map<String, Integer> indexes,
            List<SheetValidationError> errors) {
        Map<String, String> values = new HashMap<>();
        for (String header : ProductSheetMapping.HEADERS) {
            Integer index = indexes.get(header);
            String value = index == null || index >= row.size() ? null : row.get(index);
            if (value != null && value.length() > SOURCE_LIMITS.get(header)) {
                error(errors, header, "VALUE_TOO_LONG", header + " exceeds the import storage limit");
                value = value.substring(0, SOURCE_LIMITS.get(header));
            }
            values.put(header, value);
        }
        return new SheetProductRowSnapshot(values.get("product_uuid"), values.get("product_id"), values.get("sku"),
                values.get("product_name"), values.get("brand"), values.get("category"), values.get("subcategory"),
                values.get("short_description"), values.get("cost"), values.get("sale_price"),
                values.get("currency"), values.get("stock"), values.get("product_url"));
    }

    private void validateProduct(SheetProductRowSnapshot source, Product target, int mask,
            List<SheetValidationError> errors) {
        try {
            Product.create(UUID.randomUUID(), "PROD-00000000",
                    resolved(mask, "sku", source.sku(), target == null ? null : target.getSku()),
                    resolved(mask, "product_name", source.productName(), target == null ? null : target.getProductName()),
                    resolved(mask, "brand", source.brand(), target == null ? null : target.getBrand()),
                    resolved(mask, "category", source.category(), target == null ? null : target.getCategory()),
                    resolved(mask, "subcategory", source.subcategory(), target == null ? null : target.getSubcategory()),
                    resolved(mask, "short_description", source.shortDescription(), target == null ? null : target.getShortDescription()),
                    decimal(resolved(mask, "cost", source.cost(), target == null || target.getCost() == null ? null : target.getCost().toPlainString()), "cost"),
                    decimal(resolved(mask, "sale_price", source.salePrice(), target == null || target.getSalePrice() == null ? null : target.getSalePrice().toPlainString()), "sale_price"),
                    resolved(mask, "currency", source.currency(), target == null ? null : target.getCurrency()),
                    integer(resolved(mask, "stock", source.stock(), target == null || target.getStock() == null ? null : target.getStock().toString()), "stock"),
                    resolved(mask, "product_url", source.productUrl(), target == null ? null : target.getProductUrl()));
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "Product validation failed" : exception.getMessage();
            String field = message.substring(0, Math.max(0, message.indexOf(' ')));
            if (field.isBlank()) field = "product";
            error(errors, field, "INVALID_PRODUCT_VALUE", message);
        }
    }

    private String resolved(int mask, String header, String incoming, String current) {
        return ProductSheetMapping.isPresent(mask, header) ? blankToNull(incoming) : current;
    }

    private BigDecimal decimal(String value, String field) {
        if (value == null) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be numeric"); }
    }

    private Long integer(String value, String field) {
        if (value == null) return null;
        try { return Long.valueOf(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(field + " must be an integer"); }
    }

    private UUID parseUuid(String value, List<SheetValidationError> errors) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException exception) {
            error(errors, "product_uuid", "INVALID_PRODUCT_UUID", "Product UUID has an invalid format");
            return null;
        }
    }

    private void validateHeaders(List<String> headers) {
        if (headers.isEmpty() || headers.size() > ProductSheetMapping.COLUMN_COUNT)
            throw validation("INVALID_SHEET_HEADER", "headers", "The Sheet must declare at most 13 headers");
        int previous = -1;
        Set<String> seen = new HashSet<>();
        for (String header : headers) {
            if (header.isBlank() || !ProductSheetMapping.isKnownHeader(header) || !seen.add(header))
                throw validation("INVALID_SHEET_HEADER", "headers", "Headers are unknown, blank, or duplicated");
            int position = ProductSheetMapping.HEADERS.indexOf(header);
            if (position <= previous)
                throw validation("INVALID_SHEET_HEADER", "headers", "Headers must preserve canonical ordering");
            previous = position;
        }
        if (!seen.containsAll(ProductSheetMapping.REQUIRED_HEADERS))
            throw validation("INVALID_SHEET_HEADER", "headers", "Required headers are missing");
    }

    private List<List<String>> normalize(List<List<String>> raw) {
        return raw.stream().map(row -> row.stream().map(SheetSnapshotFingerprint::normalizeCell).toList()).toList();
    }

    private List<List<String>> trimTrailingEmptyRows(List<List<String>> values) {
        int end = values.size();
        while (end > 0 && values.get(end - 1).stream().allMatch(String::isBlank)) end--;
        return List.copyOf(values.subList(0, end));
    }

    private List<String> trimTrailing(List<String> row) {
        int end = row.size();
        while (end > 0 && row.get(end - 1).isBlank()) end--;
        return List.copyOf(row.subList(0, end));
    }

    private List<String> canonicalRow(List<String> row, int width) {
        List<String> result = new ArrayList<>(width);
        for (int index = 0; index < width; index++) result.add(index < row.size() ? row.get(index) : "");
        return List.copyOf(result);
    }

    private Map<String, Integer> indexes(List<String> headers) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < headers.size(); index++) result.put(headers.get(index), index);
        return result;
    }

    private void appendAudit(SheetImportJob job, AuditOperationContext context) {
        List<AuditChange> changes = new ArrayList<>();
        add(changes, "provider", null, job.getProvider().name(), AuditValueType.ENUM);
        add(changes, "spreadsheet_id", null, job.getSpreadsheetId(), AuditValueType.STRING);
        add(changes, "sheet_name", null, job.getSheetName(), AuditValueType.STRING);
        add(changes, "source_range", null, job.getSourceRange(), AuditValueType.STRING);
        add(changes, "source_fingerprint", null, job.getSourceFingerprint(), AuditValueType.STRING);
        add(changes, "header_presence_mask", null, Integer.toString(job.getHeaderPresenceMask()), AuditValueType.STRING);
        add(changes, "status", null, job.getStatus().name(), AuditValueType.ENUM);
        audit.append(new AuditEvent(UUID.randomUUID(), context, AuditAction.CREATE, ENTITY_TYPE,
                job.getImportJobUuid(), null, Instant.now(clock), changes));
    }

    private void add(List<AuditChange> changes, String field, String oldValue, String newValue, AuditValueType type) {
        changes.add(new AuditChange(field, oldValue, newValue, type, changes.size()));
    }

    private void error(List<SheetValidationError> errors, String field, String code, String message) {
        errors.add(new SheetValidationError(field, code, message));
    }

    private SheetImportValidationException validation(String code, String field, String message) {
        return new SheetImportValidationException(code, field, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record IndexedRow(int rowNumber, List<String> values) {
    }
}
