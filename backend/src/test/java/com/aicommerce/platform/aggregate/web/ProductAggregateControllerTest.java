package com.aicommerce.platform.aggregate.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicommerce.platform.aggregate.application.ProductAggregateQueryService;
import com.aicommerce.platform.aggregate.application.ProductAggregateView;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductAggregateController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class ProductAggregateControllerTest {
    private static final UUID PRODUCT = UUID.fromString("397ece0e-c859-4fa9-b894-b4ad4494f248");

    @Autowired MockMvc mvc;
    @MockitoBean ProductAggregateQueryService queryService;

    @Test
    void defaultsToActiveOnlyAndReturnsNoStoreWithoutEtag() throws Exception {
        when(queryService.get(PRODUCT, false)).thenReturn(emptyView());

        mvc.perform(get("/api/products/{productUuid}/aggregate", PRODUCT))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.knowledge").isArray());

        verify(queryService).get(PRODUCT, false);
    }

    @Test
    void acceptsOnlyOneExactBoolean() throws Exception {
        when(queryService.get(PRODUCT, true)).thenReturn(emptyView());
        mvc.perform(get("/api/products/{productUuid}/aggregate?includeArchived=true", PRODUCT))
                .andExpect(status().isOk());
        verify(queryService).get(PRODUCT, true);

        for (String query : List.of("includeArchived=True", "includeArchived=1",
                "includeArchived=", "includeArchived=true&includeArchived=false", "unexpected=true")) {
            mvc.perform(get("/api/products/{productUuid}/aggregate?" + query, PRODUCT))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("includeArchived"));
        }
    }

    @Test
    void preservesMissingProductContract() throws Exception {
        when(queryService.get(PRODUCT, false)).thenThrow(new ProductNotFoundException(PRODUCT));
        mvc.perform(get("/api/products/{productUuid}/aggregate", PRODUCT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void invalidQueryDoesNotCallService() throws Exception {
        mvc.perform(get("/api/products/{productUuid}/aggregate?includeArchived=false&x=1", PRODUCT))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(queryService);
    }

    private ProductAggregateView emptyView() {
        return new ProductAggregateView(null, List.of(), List.of(), List.of(), List.of(), null);
    }
}
