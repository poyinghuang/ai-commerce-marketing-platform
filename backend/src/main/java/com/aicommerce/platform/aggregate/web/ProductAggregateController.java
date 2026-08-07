package com.aicommerce.platform.aggregate.web;

import com.aicommerce.platform.aggregate.application.ProductAggregateQueryService;
import com.aicommerce.platform.aggregate.application.ProductAggregateView;
import com.aicommerce.platform.product.application.ProductValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productUuid}/aggregate")
public class ProductAggregateController {
    private final ProductAggregateQueryService queryService;

    public ProductAggregateController(ProductAggregateQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ProductAggregateView> get(@PathVariable UUID productUuid,
            @RequestParam MultiValueMap<String, String> parameters) {
        boolean includeArchived = parseIncludeArchived(parameters);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queryService.get(productUuid, includeArchived));
    }

    static boolean parseIncludeArchived(MultiValueMap<String, String> parameters) {
        if (!parameters.keySet().equals(parameters.isEmpty() ? java.util.Set.of() : java.util.Set.of("includeArchived"))) {
            throw new ProductValidationException("includeArchived", "only includeArchived is allowed");
        }
        List<String> values = parameters.get("includeArchived");
        if (values == null) return false;
        if (values.size() != 1 || !("true".equals(values.get(0)) || "false".equals(values.get(0)))) {
            throw new ProductValidationException("includeArchived", "includeArchived must be exactly true or false");
        }
        return Boolean.parseBoolean(values.get(0));
    }
}
