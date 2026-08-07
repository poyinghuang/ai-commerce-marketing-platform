package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.product.web.*;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {
  private static final String MERGE = "application/merge-patch+json";
  private static final Map<String, String> SORT =
      Map.of(
          "updatedAt",
          "updatedAt",
          "createdAt",
          "createdAt",
          "campaignName",
          "campaignName",
          "startDate",
          "startDate",
          "endDate",
          "endDate");
  private static final Set<String> DIR = Set.of("asc", "desc");
  private final CampaignCommandService commands;
  private final CampaignQueryService queries;
  private final CampaignMergePatchParser patches;

  public CampaignController(
      CampaignCommandService c, CampaignQueryService q, CampaignMergePatchParser p) {
    commands = c;
    queries = q;
    patches = p;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CampaignResponse> create(
      @Valid @RequestBody CreateCampaignRequest b, HttpServletRequest r) {
    CampaignPlan c = commands.create(command(b), requestId(r));
    return ResponseEntity.created(URI.create("/api/campaigns/" + c.getCampaignUuid()))
        .eTag(ResourceEtag.format(c.getVersion()))
        .body(CampaignResponse.from(c));
  }

  @GetMapping
  public CampaignPageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) UUID productUuid,
      @RequestParam(defaultValue = "ACTIVE") String associationStatus,
      @RequestParam(defaultValue = "updatedAt,desc") String sort) {
    page(page, size);
    LifecycleStatus s = status(status, "status"),
        as = productUuid == null ? null : status(associationStatus, "associationStatus");
    String z = sort(sort, SORT);
    String[] parts = z.split(",");
    Page<CampaignPlan> result =
        queries.list(
            s,
            keyword,
            productUuid,
            as,
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(parts[1]), SORT.get(parts[0]))
                    .and(Sort.by("campaignUuid"))));
    Map<UUID, com.aicommerce.platform.campaign.domain.CampaignProduct> associations =
        productUuid == null ? Map.of() : queries.associationsFor(result.getContent(), productUuid);
    List<CampaignResponse> content =
        result.getContent().stream()
            .map(
                c ->
                    CampaignResponse.from(
                        c,
                        productUuid == null
                            ? null
                            : CampaignProductResponse.from(associations.get(c.getCampaignUuid()))))
            .toList();
    return new CampaignPageResponse(
        content,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        new CampaignPageResponse.SortResponse(parts[0], parts[1]));
  }

  @GetMapping("/{id}")
  public ResponseEntity<CampaignResponse> get(@PathVariable UUID id) {
    CampaignPlan c = queries.get(id);
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(c.getVersion()))
        .body(CampaignResponse.from(c));
  }

  @PatchMapping(path = "/{id}", consumes = MERGE)
  public ResponseEntity<CampaignResponse> patch(
      @PathVariable UUID id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      @RequestBody JsonNode body,
      HttpServletRequest r) {
    CampaignPlan c = commands.patch(id, version(etag), patches.parse(body), requestId(r));
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(c.getVersion()))
        .body(CampaignResponse.from(c));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> archive(
      @PathVariable UUID id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      HttpServletRequest r) {
    CampaignPlan c = commands.archive(id, version(etag), requestId(r));
    return ResponseEntity.noContent().eTag(ResourceEtag.format(c.getVersion())).build();
  }

  @PostMapping("/{id}/restore")
  public ResponseEntity<CampaignResponse> restore(
      @PathVariable UUID id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String etag,
      HttpServletRequest r) {
    CampaignPlan c = commands.restore(id, version(etag), requestId(r));
    return ResponseEntity.ok()
        .eTag(ResourceEtag.format(c.getVersion()))
        .body(CampaignResponse.from(c));
  }

  private CreateCampaignCommand command(CreateCampaignRequest b) {
    return new CreateCampaignCommand(
        b.campaignName(),
        b.activityType(),
        b.startDate(),
        b.endDate(),
        b.objective(),
        b.platform(),
        b.budgetDaily(),
        b.budgetTotal(),
        b.currency(),
        b.promotion(),
        b.landingPage());
  }

  private long version(String e) {
    if (e == null) throw new PreconditionRequiredException();
    try {
      return ResourceEtag.parse(e);
    } catch (IllegalArgumentException x) {
      throw new InvalidIfMatchException();
    }
  }

  private void page(int p, int s) {
    if (p < 0) throw new CampaignValidationException("page", "page must be non-negative");
    if (s < 1 || s > 100)
      throw new CampaignValidationException("size", "size must be between 1 and 100");
  }

  private LifecycleStatus status(String v, String f) {
    String n = v.trim().toUpperCase(Locale.ROOT);
    if ("ALL".equals(n)) return null;
    try {
      return LifecycleStatus.valueOf(n);
    } catch (Exception e) {
      throw new CampaignValidationException(f, f + " must be ACTIVE, ARCHIVED, or ALL");
    }
  }

  private String sort(String v, Map<String, String> a) {
    String[] p = v.split(",", -1);
    if (p.length != 2 || !a.containsKey(p[0]) || !DIR.contains(p[1]))
      throw new CampaignValidationException("sort", "sort field or direction is not allowed");
    return v;
  }

  private String requestId(HttpServletRequest r) {
    return (String) r.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
  }
}
