package com.aicommerce.platform.creativeplan.web;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.creativeplan.application.*;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/products/{productUuid}/creative-plans")
public class CreativePlanController {
    private static final String MERGE_PATCH = "application/merge-patch+json";
    private static final Map<String,String> SORT = Map.of("updatedAt","updatedAt", "createdAt","createdAt", "planName","planName");
    private static final Set<String> DIRECTIONS = Set.of("asc","desc");
    private final CreativePlanCommandService commands; private final CreativePlanQueryService queries;
    private final CreativePlanMergePatchParser patches;
    public CreativePlanController(CreativePlanCommandService commands, CreativePlanQueryService queries, CreativePlanMergePatchParser patches) {
        this.commands=commands; this.queries=queries; this.patches=patches;
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreativePlanResponse> create(@PathVariable UUID productUuid,
            @Valid @RequestBody CreateCreativePlanRequest body, HttpServletRequest request) {
        CreativePlan plan = commands.create(productUuid, new CreateCreativePlanCommand(body.planName(), body.primaryAudience(),
                body.secondaryAudience(), body.painPoint(), body.coreBenefit(), body.creativeAngle(), body.emotionalDirection(),
                body.brandTone(), body.visualStyle(), body.mainColor(), body.characterSetting(), body.cta()), requestId(request));
        return ResponseEntity.created(URI.create("/api/products/"+productUuid+"/creative-plans/"+plan.getCreativePlanUuid()))
                .eTag(ResourceEtag.format(plan.getVersion())).body(CreativePlanResponse.from(plan));
    }
    @GetMapping
    public CreativePlanPageResponse list(@PathVariable UUID productUuid, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="ACTIVE") String status,
            @RequestParam(defaultValue="updatedAt,desc") String sort) {
        if (page < 0) throw new CreativePlanValidationException("page","page must be non-negative");
        if (size < 1 || size > 100) throw new CreativePlanValidationException("size","size must be between 1 and 100");
        LifecycleStatus parsedStatus = status(status); String normalizedSort = sort(sort); String[] parts=normalizedSort.split(",");
        Sort ordering = Sort.by(Sort.Direction.fromString(parts[1]), SORT.get(parts[0])).and(Sort.by("creativePlanUuid").ascending());
        return CreativePlanPageResponse.from(queries.list(productUuid, parsedStatus, PageRequest.of(page,size,ordering)), normalizedSort);
    }
    @GetMapping("/{creativePlanUuid}")
    public ResponseEntity<CreativePlanResponse> get(@PathVariable UUID productUuid, @PathVariable UUID creativePlanUuid) {
        CreativePlan plan=queries.get(productUuid,creativePlanUuid);
        return ResponseEntity.ok().eTag(ResourceEtag.format(plan.getVersion())).body(CreativePlanResponse.from(plan));
    }
    @PatchMapping(path="/{creativePlanUuid}", consumes=MERGE_PATCH)
    public ResponseEntity<CreativePlanResponse> patch(@PathVariable UUID productUuid, @PathVariable UUID creativePlanUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch, @RequestBody JsonNode body, HttpServletRequest request) {
        CreativePlan plan=commands.patch(productUuid,creativePlanUuid,version(ifMatch),patches.parse(body),requestId(request));
        return ResponseEntity.ok().eTag(ResourceEtag.format(plan.getVersion())).body(CreativePlanResponse.from(plan));
    }
    @DeleteMapping("/{creativePlanUuid}")
    public ResponseEntity<Void> archive(@PathVariable UUID productUuid, @PathVariable UUID creativePlanUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch, HttpServletRequest request) {
        CreativePlan plan=commands.archive(productUuid,creativePlanUuid,version(ifMatch),requestId(request));
        return ResponseEntity.noContent().eTag(ResourceEtag.format(plan.getVersion())).build();
    }
    @PostMapping("/{creativePlanUuid}/restore")
    public ResponseEntity<CreativePlanResponse> restore(@PathVariable UUID productUuid, @PathVariable UUID creativePlanUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch, HttpServletRequest request) {
        CreativePlan plan=commands.restore(productUuid,creativePlanUuid,version(ifMatch),requestId(request));
        return ResponseEntity.ok().eTag(ResourceEtag.format(plan.getVersion())).body(CreativePlanResponse.from(plan));
    }
    private long version(String value) { if (value==null) throw new PreconditionRequiredException(); try{return ResourceEtag.parse(value);}catch(IllegalArgumentException e){throw new InvalidIfMatchException();} }
    private LifecycleStatus status(String value) { String v=value.trim().toUpperCase(Locale.ROOT); if("ALL".equals(v)) return null; try{return LifecycleStatus.valueOf(v);}catch(Exception e){throw new CreativePlanValidationException("status","status must be ACTIVE, ARCHIVED, or ALL");} }
    private String sort(String value) { String[] p=value.split(",",-1); if(p.length!=2||!SORT.containsKey(p[0])||!DIRECTIONS.contains(p[1])) throw new CreativePlanValidationException("sort","sort field or direction is not allowed"); return p[0]+","+p[1]; }
    private String requestId(HttpServletRequest r) { return (String)r.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE); }
}
