package com.aicommerce.platform.knowledge.web;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.knowledge.application.*;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
@RestController
@RequestMapping("/api/products/{productUuid}/knowledge")
public class KnowledgeController {
    private static final String MERGE_PATCH="application/merge-patch+json";
    private static final Map<String,String> SORTS=Map.of("updatedAt","updatedAt","createdAt","createdAt","title","title","knowledgeType","knowledgeType");
    private static final Set<String> DIRECTIONS=Set.of("asc","desc");
    private final KnowledgeCommandService commands; private final KnowledgeQueryService queries; private final KnowledgeMergePatchParser parser;
    public KnowledgeController(KnowledgeCommandService commands, KnowledgeQueryService queries, KnowledgeMergePatchParser parser) { this.commands=commands; this.queries=queries; this.parser=parser; }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<KnowledgeResponse> create(@PathVariable UUID productUuid, @Valid @RequestBody CreateKnowledgeRequest request, HttpServletRequest http) {
        ProductKnowledge value=commands.create(productUuid,new CreateKnowledgeCommand(request.knowledgeType(),request.title(),request.content(),request.source()),requestId(http));
        return ResponseEntity.created(URI.create("/api/products/"+productUuid+"/knowledge/"+value.getKnowledgeUuid())).eTag(ResourceEtag.format(value.getVersion())).body(KnowledgeResponse.from(value));
    }
    @GetMapping public KnowledgePageResponse list(@PathVariable UUID productUuid,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="ACTIVE") String status,@RequestParam(defaultValue="updatedAt,desc") String sort) {
        if(page<0) throw new KnowledgeValidationException("page","page must be non-negative"); if(size<1||size>100) throw new KnowledgeValidationException("size","size must be between 1 and 100");
        LifecycleStatus parsed=parseStatus(status); String normalized=sort(sort); String[] parts=normalized.split(",");
        Sort s=Sort.by(Sort.Direction.fromString(parts[1]),SORTS.get(parts[0])).and(Sort.by("knowledgeUuid").ascending());
        return KnowledgePageResponse.from(queries.list(productUuid,parsed,PageRequest.of(page,size,s)),status.toUpperCase(Locale.ROOT),normalized);
    }
    @GetMapping("/{knowledgeUuid}") public ResponseEntity<KnowledgeResponse> get(@PathVariable UUID productUuid,@PathVariable UUID knowledgeUuid) { var value=queries.get(productUuid,knowledgeUuid); return ResponseEntity.ok().eTag(ResourceEtag.format(value.getVersion())).body(KnowledgeResponse.from(value)); }
    @PatchMapping(path="/{knowledgeUuid}",consumes=MERGE_PATCH) public ResponseEntity<KnowledgeResponse> patch(@PathVariable UUID productUuid,@PathVariable UUID knowledgeUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String etag,@RequestBody JsonNode patch,HttpServletRequest http) { var value=commands.patch(productUuid,knowledgeUuid,version(etag),parser.parse(patch),requestId(http)); return ResponseEntity.ok().eTag(ResourceEtag.format(value.getVersion())).body(KnowledgeResponse.from(value)); }
    @DeleteMapping("/{knowledgeUuid}") public ResponseEntity<Void> archive(@PathVariable UUID productUuid,@PathVariable UUID knowledgeUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String etag,HttpServletRequest http) { var value=commands.archive(productUuid,knowledgeUuid,version(etag),requestId(http)); return ResponseEntity.noContent().eTag(ResourceEtag.format(value.getVersion())).build(); }
    @PostMapping("/{knowledgeUuid}/restore") public ResponseEntity<KnowledgeResponse> restore(@PathVariable UUID productUuid,@PathVariable UUID knowledgeUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String etag,HttpServletRequest http) { var value=commands.restore(productUuid,knowledgeUuid,version(etag),requestId(http)); return ResponseEntity.ok().eTag(ResourceEtag.format(value.getVersion())).body(KnowledgeResponse.from(value)); }
    private long version(String value) { if(value==null||value.isBlank()) throw new PreconditionRequiredException(); try{return ResourceEtag.parse(value);}catch(IllegalArgumentException e){throw new InvalidIfMatchException();} }
    private LifecycleStatus parseStatus(String value) { String normalized=value.trim().toUpperCase(Locale.ROOT); if("ALL".equals(normalized)) return null; try{return LifecycleStatus.valueOf(normalized);}catch(Exception e){throw new KnowledgeValidationException("status","status must be ACTIVE, ARCHIVED, or ALL");} }
    private String sort(String value) { String[] p=value.split(",",-1); if(p.length!=2||!SORTS.containsKey(p[0])||!DIRECTIONS.contains(p[1])) throw new KnowledgeValidationException("sort","sort field or direction is not allowed"); return p[0]+","+p[1]; }
    private String requestId(HttpServletRequest r){return (String)r.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);}
}
