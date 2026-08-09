package com.aicommerce.platform.connector.sheets.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.connector.sheets.application.SheetImportExecutionService;
import com.aicommerce.platform.connector.sheets.application.SheetImportPreconditionFailedException;
import com.aicommerce.platform.connector.sheets.application.SheetImportPreviewService;
import com.aicommerce.platform.connector.sheets.application.SheetImportQueryService;
import com.aicommerce.platform.connector.sheets.application.SheetImportView;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SheetImportController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class SheetImportControllerTest {
    private static final UUID JOB_UUID = UUID.fromString("87083fe5-311f-4eba-b6ba-f3e22e79887c");

    @Autowired MockMvc mvc;
    @MockitoBean SheetImportPreviewService previews;
    @MockitoBean SheetImportQueryService queries;
    @MockitoBean SheetImportExecutionService executions;

    @Test
    void previewReturnsLocationWeakEtagAndStandardBody() throws Exception {
        when(previews.preview(any(), anyString())).thenReturn(view(0, "PREVIEWED"));

        mvc.perform(post("/api/connectors/google-sheets/imports/preview")
                        .header("X-Request-ID", "sheet-web-preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spreadsheetId":"stub-products","sheetName":"Products"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/connectors/google-sheets/imports/" + JOB_UUID))
                .andExpect(header().string("ETag", "W/\"0\""))
                .andExpect(jsonPath("$.headerPresenceMask").value(8191));
    }

    @Test
    void templateAndGetExposeFixedContracts() throws Exception {
        when(queries.get(JOB_UUID)).thenReturn(view(2, "COMPLETED"));
        mvc.perform(get("/api/connectors/google-sheets/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=google-sheets-product-import-template.csv"));
        mvc.perform(get("/api/connectors/google-sheets/imports/{jobUuid}", JOB_UUID))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "W/\"2\""));
    }

    @Test
    void executeRequiresValidCurrentWeakEtag() throws Exception {
        mvc.perform(post("/api/connectors/google-sheets/imports/{jobUuid}/execute", JOB_UUID))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        mvc.perform(post("/api/connectors/google-sheets/imports/{jobUuid}/execute", JOB_UUID)
                        .header("If-Match", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));

        when(executions.execute(any(), anyLong(), anyString()))
                .thenThrow(new SheetImportPreconditionFailedException());
        mvc.perform(post("/api/connectors/google-sheets/imports/{jobUuid}/execute", JOB_UUID)
                        .header("If-Match", "W/\"0\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("IMPORT_JOB_STALE"));
    }

    private SheetImportView view(long version, String status) {
        Instant now = Instant.parse("2026-08-09T00:00:00Z");
        return new SheetImportView(JOB_UUID, "GOOGLE_SHEETS", "stub-products", "Products",
                "'Products'!A1:M1001", "a".repeat(64), 8191, status, 1, 1, 0,
                "COMPLETED".equals(status) ? 1 : 0, 0, 0, null, null, now, now, version, List.of());
    }
}
