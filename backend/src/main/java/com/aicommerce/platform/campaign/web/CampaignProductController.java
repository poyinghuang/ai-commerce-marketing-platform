package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.product.web.*;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/campaigns/{campaignUuid}/products")
public class CampaignProductController {
  private static final String MERGE = "application/merge-patch+json";
  private static final Map<String, String> SORT =
      Map.of(
          "updatedAt",
          "updatedAt",
          "createdAt",
          "createdAt",
          "priority",
          "priority",
          "budgetWeight",
          "budgetWeight");
  private static final Set<String> DIR = Set.of("asc", "desc");
  private final CampaignCommandService commands;
  private final CampaignQueryService queries;
  private final CampaignProductMergePatchParser patches;

  public CampaignProductController(
      CampaignCommandService c, CampaignQueryService q, CampaignProductMergePatchParser p) {
    commands = c;
    queries = q;
    patches = p;
  }

  @PostMapping
  public ResponseEntity<CampaignProductResponse> create(
      @PathVariable UUID campaignUuid,
      @Valid @RequestBody CreateCampaignProductRequest b,
      HttpServletRequest r) {
    CampaignProduct cp =
        commands.addProduct(
            campaignUuid,
            new CreateCampaignProductCommand(
                b.productUuid(), b.role(), b.priority(), b.budgetWeight()),
            requestId(r));
    return ResponseEntity.created(
            URI.create("/api/campaigns/" + campaignUuid + "/products/" + cp.getProductUuid()))
        .eTag(ResourceEtag.format(cp.getVersion()))
        .body(CampaignProductResponse.from(cp));
  }

  @GetMapping
  public CampaignProductPageResponse list(
      @PathVariable UUID campaignUuid,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(defaultValue = "updatedAt,desc") String sort) {
    if (page < 0) throw new CampaignValidationException("page", "page must be non-negative");
    if (size < 1 || size > 100)
      throw new CampaignValidationException("size", "size must be between 1 and 100");
    LifecycleStatus s = status(status);
    String z = sort(sort);
    String[] p = z.split(",");
    return CampaignProductPageResponse.from(
        queries.listProducts(
            campaignUuid,
            s,
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(p[1]), SORT.get(p[0]))
                    .and(Sort.by("campaignProductUuid")))),
        z);
  }

  @GetMapping("/{productUuid}")
  public ResponseEntity<CampaignProductResponse> get(
      @PathVariable UUID campaignUuid, @PathVariable UUID productUuid) {
    CampaignProduct cp = queries.getProduct(campaignUuid, productUuid);
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(cp.getVersion()))
        .body(CampaignProductResponse.from(cp));
  }

  @PatchMapping(path = "/{productUuid}", consumes = MERGE)
  public ResponseEntity<CampaignProductResponse> patch(
      @PathVariable UUID campaignUuid,
      @PathVariable UUID productUuid,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      @RequestBody JsonNode body,
      HttpServletRequest r) {
    CampaignProduct cp =
        commands.patchProduct(
            campaignUuid, productUuid, version(etag), patches.parse(body), requestId(r));
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(cp.getVersion()))
        .body(CampaignProductResponse.from(cp));
  }

  @DeleteMapping("/{productUuid}")
  public ResponseEntity<Void> archive(
      @PathVariable UUID campaignUuid,
      @PathVariable UUID productUuid,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      HttpServletRequest r) {
    CampaignProduct cp =
        commands.archiveProduct(campaignUuid, productUuid, version(etag), requestId(r));
    return ResponseEntity.noContent().eTag(ResourceEtag.format(cp.getVersion())).build();
  }

  @PostMapping("/{productUuid}/restore")
  public ResponseEntity<CampaignProductResponse> restore(
      @PathVariable UUID campaignUuid,
      @PathVariable UUID productUuid,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      HttpServletRequest r) {
    CampaignProduct cp =
        commands.restoreProduct(campaignUuid, productUuid, version(etag), requestId(r));
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(cp.getVersion()))
        .body(CampaignProductResponse.from(cp));
  }

  private long version(String e) {
    if (e == null) throw new PreconditionRequiredException();
    try {
      return ResourceEtag.parse(e);
    } catch (IllegalArgumentException x) {
      throw new InvalidIfMatchException();
    }
  }

  private LifecycleStatus status(String v) {
    String n = v.trim().toUpperCase(Locale.ROOT);
    if ("ALL".equals(n)) return null;
    try {
      return LifecycleStatus.valueOf(n);
    } catch (Exception e) {
      throw new CampaignValidationException("status", "status must be ACTIVE, ARCHIVED, or ALL");
    }
  }

  private String sort(String v) {
    String[] p = v.split(",", -1);
    if (p.length != 2 || !SORT.containsKey(p[0]) || !DIR.contains(p[1]))
      throw new CampaignValidationException("sort", "sort field or direction is not allowed");
    return v;
  }

  private String requestId(HttpServletRequest r) {
    return (String) r.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
  }
}
