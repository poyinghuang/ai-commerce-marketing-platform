package com.aicommerce.platform.product.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.application.ProductPreconditionFailedException;
import com.aicommerce.platform.product.application.ProductQueryService;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import({
        ProductRequestMapper.class,
        ProductMergePatchParser.class,
        GlobalExceptionHandler.class,
        RequestIdFilter.class
})
class ProductControllerTest {

    private static final UUID PRODUCT_UUID = UUID.fromString("d4476a19-30ed-48d9-a518-f9b111bd0911");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductCommandService commandService;

    @MockitoBean
    ProductQueryService queryService;

    @Test
    void createReturnsLocationAndWeakEtag() throws Exception {
        when(commandService.create(any(), anyString())).thenReturn(product());

        mockMvc.perform(post("/api/products")
                        .header(RequestIdFilter.HEADER_NAME, "create-product-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku":"SKU-1",
                                  "productName":"Product One",
                                  "salePrice":"20.0000",
                                  "currency":"TWD",
                                  "stock":"5"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/products/" + PRODUCT_UUID))
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""))
                .andExpect(jsonPath("$.productUuid").value(PRODUCT_UUID.toString()))
                .andExpect(jsonPath("$.productName").value("Product One"));
    }

    @Test
    void getReturnsWeakEtag() throws Exception {
        when(queryService.findByUuid(PRODUCT_UUID)).thenReturn(product());

        mockMvc.perform(get("/api/products/{productUuid}", PRODUCT_UUID))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""));
    }

    @Test
    void patchRequiresValidIfMatchAndRejectsImmutableFields() throws Exception {
        mockMvc.perform(patch("/api/products/{productUuid}", PRODUCT_UUID)
                        .contentType("application/merge-patch+json")
                        .content("{\"brand\":\"Updated\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        mockMvc.perform(patch("/api/products/{productUuid}", PRODUCT_UUID)
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"productId\":\"PROD-00000099\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MERGE_PATCH"));
    }

    @Test
    void stalePatchReturnsPreconditionFailed() throws Exception {
        when(commandService.patch(any(), anyLong(), any(), anyString()))
                .thenThrow(new ProductPreconditionFailedException());

        mockMvc.perform(patch("/api/products/{productUuid}", PRODUCT_UUID)
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"brand\":\"Updated\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void archiveReturnsNoContentAndCurrentEtag() throws Exception {
        when(commandService.archive(any(), anyLong(), anyString())).thenReturn(product());

        mockMvc.perform(delete("/api/products/{productUuid}", PRODUCT_UUID)
                        .header(HttpHeaders.IF_MATCH, "W/\"0\""))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""));
    }

    @Test
    void listRejectsOversizedPageAndUnapprovedSort() throws Exception {
        mockMvc.perform(get("/api/products?size=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

        mockMvc.perform(get("/api/products?sort=productUuid,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    private Product product() {
        return Product.create(
                PRODUCT_UUID,
                "PROD-00000001",
                "SKU-1",
                "Product One",
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("20.0000"),
                "TWD",
                5L,
                null);
    }
}
