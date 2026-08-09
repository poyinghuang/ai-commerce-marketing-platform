package com.aicommerce.platform.connector.sheets.application;

import java.math.BigDecimal;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.connector.sheets.domain.SheetImportExecutionStatus;
import com.aicommerce.platform.connector.sheets.domain.SheetImportJob;
import com.aicommerce.platform.connector.sheets.domain.SheetImportPlannedAction;
import com.aicommerce.platform.connector.sheets.domain.SheetImportRow;
import com.aicommerce.platform.connector.sheets.domain.SheetProductRowSnapshot;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportJobJpaRepository;
import com.aicommerce.platform.connector.sheets.infrastructure.persistence.SheetImportRowJpaRepository;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.PatchField;
import com.aicommerce.platform.product.application.PatchProductCommand;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SheetImportRowSuccessService {
    private final SheetImportRowJpaRepository rows;
    private final SheetImportJobJpaRepository jobs;
    private final ProductCommandService products;

    public SheetImportRowSuccessService(SheetImportRowJpaRepository rows, SheetImportJobJpaRepository jobs,
            ProductCommandService products) {
        this.rows = rows;
        this.jobs = jobs;
        this.products = products;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(UUID rowUuid, AuditOperationContext context) {
        SheetImportRow row = rows.findById(rowUuid).orElseThrow();
        if (row.getExecutionStatus() != SheetImportExecutionStatus.PENDING) return;
        SheetImportJob job = jobs.findById(row.getImportJobUuid()).orElseThrow(SheetImportNotFoundException::new);
        SheetProductRowSnapshot source = row.getSnapshot();
        Product result;
        if (row.getPlannedAction() == SheetImportPlannedAction.CREATE) {
            result = products.create(create(source), context);
        } else if (row.getPlannedAction() == SheetImportPlannedAction.UPDATE) {
            result = products.patch(row.getTargetProductUuid(), row.getTargetProductVersion(),
                    patch(source, job.getHeaderPresenceMask()), context);
        } else {
            throw new IllegalStateException("Invalid rows cannot execute");
        }
        row.recordSuccess(result.getProductUuid(), result.getProductId());
        rows.saveAndFlush(row);
    }

    private CreateProductCommand create(SheetProductRowSnapshot value) {
        return new CreateProductCommand(blank(value.sku()), blank(value.productName()), blank(value.brand()),
                blank(value.category()), blank(value.subcategory()), blank(value.shortDescription()),
                decimal(value.cost()), decimal(value.salePrice()), blank(value.currency()), integer(value.stock()),
                blank(value.productUrl()));
    }

    private PatchProductCommand patch(SheetProductRowSnapshot value, int mask) {
        return new PatchProductCommand(field(mask, "sku", value.sku(), this::blank),
                field(mask, "product_name", value.productName(), this::blank),
                field(mask, "brand", value.brand(), this::blank),
                field(mask, "category", value.category(), this::blank),
                field(mask, "subcategory", value.subcategory(), this::blank),
                field(mask, "short_description", value.shortDescription(), this::blank),
                field(mask, "cost", value.cost(), this::decimal),
                field(mask, "sale_price", value.salePrice(), this::decimal),
                field(mask, "currency", value.currency(), this::blank),
                field(mask, "stock", value.stock(), this::integer),
                field(mask, "product_url", value.productUrl(), this::blank));
    }

    private <T> PatchField<T> field(int mask, String header, String value,
            java.util.function.Function<String, T> converter) {
        return ProductSheetMapping.isPresent(mask, header)
                ? PatchField.present(converter.apply(value))
                : PatchField.absent();
    }

    private String blank(String value) { return value == null || value.isBlank() ? null : value; }
    private BigDecimal decimal(String value) { return blank(value) == null ? null : new BigDecimal(value); }
    private Long integer(String value) { return blank(value) == null ? null : Long.valueOf(value); }
}
