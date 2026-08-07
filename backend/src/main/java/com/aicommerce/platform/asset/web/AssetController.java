package com.aicommerce.platform.asset.web;

import com.aicommerce.platform.asset.application.*;
import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/products/{productUuid}/assets")
public class AssetController {
    private static final String MERGE_PATCH="application/merge-patch+json";
    private static final Map<String,String> SORT=Map.of("updatedAt","updatedAt","createdAt","createdAt",
            "assetType","assetType","originalFilename","originalFilename","sizeBytes","sizeBytes");
    private static final Set<String> DIRECTIONS=Set.of("asc","desc");
    private final AssetCommandService commands; private final AssetQueryService queries; private final AssetMergePatchParser patches;
    public AssetController(AssetCommandService commands,AssetQueryService queries,AssetMergePatchParser patches) {
        this.commands=commands;this.queries=queries;this.patches=patches;
    }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AssetResponse> create(@PathVariable UUID productUuid,@Valid @RequestBody CreateAssetRequest body,
            HttpServletRequest request) {
        Asset asset=commands.create(productUuid,new CreateAssetCommand(body.creativePlanUuid(),body.campaignUuid(),body.assetType(),
                body.purpose(),body.storageProvider(),body.providerFileId(),body.fileUrl(),body.mediaType(),body.originalFilename(),
                body.sizeBytes(),body.checksumSha256(),patches.parseProviderMetadata(body.providerMetadata())),requestId(request));
        return ResponseEntity.created(URI.create("/api/products/"+productUuid+"/assets/"+asset.getAssetUuid()))
                .eTag(ResourceEtag.format(asset.getVersion())).body(AssetResponse.from(asset));
    }
    @GetMapping
    public AssetPageResponse list(@PathVariable UUID productUuid,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="ACTIVE") String status,
            @RequestParam(required=false) String assetType,@RequestParam(required=false) UUID creativePlanUuid,
            @RequestParam(required=false) UUID campaignUuid,@RequestParam(required=false) String storageProvider,
            @RequestParam(defaultValue="updatedAt,desc") String sort) {
        if(page<0) throw new AssetValidationException("page","page must be non-negative");
        if(size<1||size>100) throw new AssetValidationException("size","size must be between 1 and 100");
        LifecycleStatus parsedStatus=status(status); AssetType parsedType=assetType(assetType); String normalizedSort=sort(sort);
        String[] parts=normalizedSort.split(",");
        Sort order=Sort.by(Sort.Direction.fromString(parts[1]),SORT.get(parts[0])).and(Sort.by("assetUuid").ascending());
        String provider=storageProvider==null||storageProvider.isBlank()?null:storageProvider.trim();
        return AssetPageResponse.from(queries.list(productUuid,parsedStatus,parsedType,creativePlanUuid,campaignUuid,provider,
                PageRequest.of(page,size,order)),normalizedSort);
    }
    @GetMapping("/{assetUuid}")
    public ResponseEntity<AssetResponse> get(@PathVariable UUID productUuid,@PathVariable UUID assetUuid) {
        Asset asset=queries.get(productUuid,assetUuid);
        return ResponseEntity.ok().eTag(ResourceEtag.format(asset.getVersion())).body(AssetResponse.from(asset));
    }
    @PatchMapping(path="/{assetUuid}",consumes=MERGE_PATCH)
    public ResponseEntity<AssetResponse> patch(@PathVariable UUID productUuid,@PathVariable UUID assetUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch,@RequestBody JsonNode body,HttpServletRequest request) {
        Asset asset=commands.patch(productUuid,assetUuid,version(ifMatch),patches.parse(body),requestId(request));
        return ResponseEntity.ok().eTag(ResourceEtag.format(asset.getVersion())).body(AssetResponse.from(asset));
    }
    @DeleteMapping("/{assetUuid}")
    public ResponseEntity<Void> archive(@PathVariable UUID productUuid,@PathVariable UUID assetUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch,HttpServletRequest request) {
        Asset asset=commands.archive(productUuid,assetUuid,version(ifMatch),requestId(request));
        return ResponseEntity.noContent().eTag(ResourceEtag.format(asset.getVersion())).build();
    }
    @PostMapping("/{assetUuid}/restore")
    public ResponseEntity<AssetResponse> restore(@PathVariable UUID productUuid,@PathVariable UUID assetUuid,
            @RequestHeader(value=HttpHeaders.IF_MATCH,required=false) String ifMatch,HttpServletRequest request) {
        Asset asset=commands.restore(productUuid,assetUuid,version(ifMatch),requestId(request));
        return ResponseEntity.ok().eTag(ResourceEtag.format(asset.getVersion())).body(AssetResponse.from(asset));
    }
    private long version(String value) { if(value==null) throw new PreconditionRequiredException(); try{return ResourceEtag.parse(value);}catch(Exception e){throw new InvalidIfMatchException();} }
    private LifecycleStatus status(String value) { String v=value.trim().toUpperCase(Locale.ROOT); if("ALL".equals(v))return null; try{return LifecycleStatus.valueOf(v);}catch(Exception e){throw new AssetValidationException("status","status must be ACTIVE, ARCHIVED, or ALL");} }
    private AssetType assetType(String value) { if(value==null||value.isBlank())return null; try{return AssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));}catch(Exception e){throw new AssetValidationException("assetType","assetType must be IMAGE, VIDEO, DOCUMENT, or OTHER");} }
    private String sort(String value) { String[] p=value.split(",",-1);if(p.length!=2||!SORT.containsKey(p[0])||!DIRECTIONS.contains(p[1]))throw new AssetValidationException("sort","sort field or direction is not allowed");return p[0]+","+p[1]; }
    private String requestId(HttpServletRequest request){return (String)request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);}
}
