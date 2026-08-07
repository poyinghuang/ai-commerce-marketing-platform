package com.aicommerce.platform.quality.web;

import java.util.UUID;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.quality.application.ProductQualityCommandService;
import com.aicommerce.platform.quality.application.ProductQualityQueryService;
import com.aicommerce.platform.quality.application.QualityProjectionView;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/products/{productUuid}/quality")
public class ProductQualityController {
    private static final String MERGE_PATCH = "application/merge-patch+json";
    private final ProductQualityQueryService queries;
    private final ProductQualityCommandService commands;
    private final QualityMergePatchParser parser;
    public ProductQualityController(ProductQualityQueryService queries, ProductQualityCommandService commands,
            QualityMergePatchParser parser) {
        this.queries = queries; this.commands = commands; this.parser = parser;
    }
    @GetMapping
    public ResponseEntity<QualityProjectionView> get(@PathVariable UUID productUuid) {
        var value = queries.get(productUuid);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .eTag(ResourceEtag.format(value.version())).body(value);
    }
    @PatchMapping(path = "/manual-adjustment", consumes = MERGE_PATCH)
    public ResponseEntity<QualityProjectionView> adjust(@PathVariable UUID productUuid,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
            @RequestBody JsonNode patch, HttpServletRequest request) {
        var value = commands.adjust(productUuid, version(etag), parser.parse(patch), requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .eTag(ResourceEtag.format(value.version())).body(value);
    }
    private long version(String value) {
        if (value == null || value.isBlank()) throw new PreconditionRequiredException();
        try { return ResourceEtag.parse(value); }
        catch (IllegalArgumentException exception) { throw new InvalidIfMatchException(); }
    }
    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }
}
