package com.aicommerce.platform.connector.drive.web;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.connector.drive.application.*;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productUuid}/storage-folder")
public class ProductStorageFolderController {
    private final ProductStorageFolderService commands;private final ProductStorageFolderQueryService queries;
    public ProductStorageFolderController(ProductStorageFolderService commands,ProductStorageFolderQueryService queries){this.commands=commands;this.queries=queries;}
    @GetMapping public ResponseEntity<ProductStorageFolderView> get(@PathVariable UUID productUuid){var view=queries.get(productUuid);return ResponseEntity.ok().eTag(ResourceEtag.format(view.version())).body(view);}
    @PostMapping public ResponseEntity<ProductStorageFolderView> ensure(@PathVariable UUID productUuid,HttpServletRequest request){
        var result=commands.ensure(productUuid,(String)request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        if(result.created())return ResponseEntity.created(URI.create("/api/products/"+productUuid+"/storage-folder")).eTag(ResourceEtag.format(result.folder().version())).body(result.folder());
        return ResponseEntity.ok().eTag(ResourceEtag.format(result.folder().version())).body(result.folder());
    }
}
