package com.aicommerce.platform.product.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.application.ProductQueryService;
import com.aicommerce.platform.product.application.ProductSearchCriteria;
import com.aicommerce.platform.product.application.ProductValidationException;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.web.RequestIdFilter;
import tools.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String MERGE_PATCH = "application/merge-patch+json";
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "updatedAt", "updatedAt",
            "createdAt", "createdAt",
            "productName", "productName",
            "productId", "productId",
            "salePrice", "salePrice",
            "stock", "stock");
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;
    private final ProductRequestMapper requestMapper;
    private final ProductMergePatchParser patchParser;

    public ProductController(
            ProductCommandService commandService,
            ProductQueryService queryService,
            ProductRequestMapper requestMapper,
            ProductMergePatchParser patchParser) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.requestMapper = requestMapper;
        this.patchParser = patchParser;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest servletRequest) {
        Product product = commandService.create(requestMapper.toCommand(request), requestId(servletRequest));
        URI location = URI.create("/api/products/" + product.getProductUuid());
        return ResponseEntity.created(location)
                .eTag(ProductEtag.fromVersion(product.getVersion()))
                .body(ProductResponse.from(product));
    }

    @GetMapping
    public ProductPageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String productId,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        if (page < 0) {
            throw new ProductValidationException("page", "page must be non-negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ProductValidationException("size", "size must be between 1 and 100");
        }
        ProductLifecycleStatus lifecycleStatus = parseStatus(status);
        String normalizedSort = normalizeSort(sort);
        String[] sortParts = normalizedSort.split(",", -1);
        Sort.Direction direction = Sort.Direction.fromString(sortParts[1]);
        Sort pageSort = Sort.by(direction, SORT_FIELDS.get(sortParts[0])).and(Sort.by("productUuid").ascending());
        Page<Product> products = queryService.search(
                new ProductSearchCriteria(lifecycleStatus, category, keyword, sku, productId),
                PageRequest.of(page, size, pageSort));
        return ProductPageResponse.from(products, normalizedSort);
    }

    @GetMapping("/{productUuid}")
    public ResponseEntity<ProductResponse> get(@PathVariable UUID productUuid) {
        Product product = queryService.findByUuid(productUuid);
        return ResponseEntity.ok()
                .eTag(ProductEtag.fromVersion(product.getVersion()))
                .body(ProductResponse.from(product));
    }

    @PatchMapping(path = "/{productUuid}", consumes = MERGE_PATCH)
    public ResponseEntity<ProductResponse> patch(
            @PathVariable UUID productUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody JsonNode patch,
            HttpServletRequest servletRequest) {
        Product product = commandService.patch(
                productUuid,
                ProductEtag.requireVersion(ifMatch),
                patchParser.parse(patch),
                requestId(servletRequest));
        return ResponseEntity.ok()
                .eTag(ProductEtag.fromVersion(product.getVersion()))
                .body(ProductResponse.from(product));
    }

    @DeleteMapping("/{productUuid}")
    public ResponseEntity<Void> archive(
            @PathVariable UUID productUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            HttpServletRequest servletRequest) {
        Product product = commandService.archive(
                productUuid,
                ProductEtag.requireVersion(ifMatch),
                requestId(servletRequest));
        return ResponseEntity.noContent()
                .eTag(ProductEtag.fromVersion(product.getVersion()))
                .build();
    }

    @PostMapping("/{productUuid}/restore")
    public ResponseEntity<ProductResponse> restore(
            @PathVariable UUID productUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            HttpServletRequest servletRequest) {
        Product product = commandService.restore(
                productUuid,
                ProductEtag.requireVersion(ifMatch),
                requestId(servletRequest));
        return ResponseEntity.ok()
                .eTag(ProductEtag.fromVersion(product.getVersion()))
                .body(ProductResponse.from(product));
    }

    private ProductLifecycleStatus parseStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalized)) {
            return null;
        }
        try {
            return ProductLifecycleStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ProductValidationException("status", "status must be ACTIVE, ARCHIVED, or ALL");
        }
    }

    private String normalizeSort(String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !SORT_FIELDS.containsKey(parts[0]) || !SORT_DIRECTIONS.contains(parts[1])) {
            throw new ProductValidationException("sort", "sort field or direction is not allowed");
        }
        return parts[0] + "," + parts[1];
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }
}
