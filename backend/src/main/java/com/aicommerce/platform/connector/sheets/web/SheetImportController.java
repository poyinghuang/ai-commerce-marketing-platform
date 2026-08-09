package com.aicommerce.platform.connector.sheets.web;

import java.net.URI;
import java.util.UUID;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.connector.sheets.application.PreviewSheetImportCommand;
import com.aicommerce.platform.connector.sheets.application.ProductSheetMapping;
import com.aicommerce.platform.connector.sheets.application.SheetImportPreviewService;
import com.aicommerce.platform.connector.sheets.application.SheetImportQueryService;
import com.aicommerce.platform.connector.sheets.application.SheetImportView;
import com.aicommerce.platform.connector.sheets.application.SheetImportExecutionService;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connectors/google-sheets")
public class SheetImportController {
    private final SheetImportPreviewService previews;
    private final SheetImportQueryService queries;
    private final SheetImportExecutionService executions;

    public SheetImportController(SheetImportPreviewService previews, SheetImportQueryService queries,
            SheetImportExecutionService executions) {
        this.previews = previews;
        this.queries = queries;
        this.executions = executions;
    }

    @GetMapping(path = "/template", produces = "text/csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=google-sheets-product-import-template.csv")
                .body(String.join(",", ProductSheetMapping.HEADERS) + "\n");
    }

    @PostMapping(path = "/imports/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SheetImportView> preview(@Valid @RequestBody PreviewSheetImportRequest request,
            HttpServletRequest servletRequest) {
        SheetImportView result = previews.preview(new PreviewSheetImportCommand(
                request.spreadsheetId(), request.sheetName(), request.range()), requestId(servletRequest));
        return ResponseEntity.created(URI.create("/api/connectors/google-sheets/imports/" + result.importJobUuid()))
                .eTag(ResourceEtag.format(result.version()))
                .body(result);
    }

    @GetMapping("/imports/{importJobUuid}")
    public ResponseEntity<SheetImportView> get(@PathVariable UUID importJobUuid) {
        SheetImportView result = queries.get(importJobUuid);
        return ResponseEntity.ok().eTag(ResourceEtag.format(result.version())).body(result);
    }

    @PostMapping("/imports/{importJobUuid}/execute")
    public ResponseEntity<SheetImportView> execute(@PathVariable UUID importJobUuid,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest servletRequest) {
        SheetImportView result = executions.execute(importJobUuid, requireVersion(ifMatch), requestId(servletRequest));
        return ResponseEntity.ok().eTag(ResourceEtag.format(result.version())).body(result);
    }

    private long requireVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) throw new PreconditionRequiredException();
        try { return ResourceEtag.parse(ifMatch); }
        catch (IllegalArgumentException exception) { throw new InvalidIfMatchException(); }
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
    }
}
