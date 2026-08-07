package com.aicommerce.platform.knowledge.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.UUID;
import com.aicommerce.platform.knowledge.application.*;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KnowledgeController.class)
@Import({KnowledgeMergePatchParser.class,GlobalExceptionHandler.class,RequestIdFilter.class})
class KnowledgeControllerTest {
    static final UUID PRODUCT=UUID.fromString("d4476a19-30ed-48d9-a518-f9b111bd0911");
    static final UUID KNOWLEDGE=UUID.fromString("5cf53b23-eabe-4b51-b565-62dbe4333721");
    @Autowired MockMvc mvc;
    @MockitoBean KnowledgeCommandService commands;
    @MockitoBean KnowledgeQueryService queries;
    @Test void createReturnsLocationAndEtag() throws Exception { when(commands.create(eq(PRODUCT),any(),anyString())).thenReturn(value()); mvc.perform(post("/api/products/{p}/knowledge",PRODUCT).contentType(MediaType.APPLICATION_JSON).content("{\"knowledgeType\":\"FEATURE\",\"title\":\"Title\",\"content\":\"Content\"}")).andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\"")).andExpect(header().string(HttpHeaders.LOCATION,"/api/products/"+PRODUCT+"/knowledge/"+KNOWLEDGE)); }
    @Test void getAndListReturnResources() throws Exception { when(queries.get(PRODUCT,KNOWLEDGE)).thenReturn(value()); when(queries.list(eq(PRODUCT),any(),any())).thenReturn(new PageImpl<>(List.of(value()))); mvc.perform(get("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE)).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\"")); mvc.perform(get("/api/products/{p}/knowledge?status=ALL&sort=title,asc",PRODUCT)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].title").value("Title")); }
    @Test void patchRequiresEtagAndRejectsUnknownField() throws Exception { mvc.perform(patch("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE).contentType("application/merge-patch+json").content("{}" )).andExpect(status().isPreconditionRequired()); mvc.perform(patch("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"productUuid\":null}" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MERGE_PATCH")); }
    @Test void patchArchiveAndRestorePreserveConcurrencyContract() throws Exception { when(commands.patch(eq(PRODUCT),eq(KNOWLEDGE),eq(0L),any(),anyString())).thenReturn(value()); when(commands.archive(eq(PRODUCT),eq(KNOWLEDGE),eq(0L),anyString())).thenReturn(value()); when(commands.restore(eq(PRODUCT),eq(KNOWLEDGE),eq(0L),anyString())).thenReturn(value()); mvc.perform(patch("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"title\":\"Updated\"}" )).andExpect(status().isOk()); mvc.perform(delete("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE).header(HttpHeaders.IF_MATCH,"W/\"0\"")).andExpect(status().isNoContent()); mvc.perform(post("/api/products/{p}/knowledge/{k}/restore",PRODUCT,KNOWLEDGE).header(HttpHeaders.IF_MATCH,"W/\"0\"")).andExpect(status().isOk()); }
    @Test void validatesPaginationSortAndMalformedEtag() throws Exception { mvc.perform(get("/api/products/{p}/knowledge?size=101",PRODUCT)).andExpect(status().isBadRequest()); mvc.perform(get("/api/products/{p}/knowledge?sort=productUuid,asc",PRODUCT)).andExpect(status().isBadRequest()); mvc.perform(delete("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE).header(HttpHeaders.IF_MATCH,"0")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH")); }
    @Test void mapsStaleMutationToPreconditionFailed() throws Exception {
        when(commands.patch(eq(PRODUCT),eq(KNOWLEDGE),eq(0L),any(),anyString()))
                .thenThrow(new KnowledgePreconditionFailedException());

        mvc.perform(patch("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE)
                        .header(HttpHeaders.IF_MATCH,"W/\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"title\":\"Stale\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    @Test void mapsArchivedKnowledgeAndProductToConflict() throws Exception {
        when(commands.patch(eq(PRODUCT),eq(KNOWLEDGE),eq(0L),any(),anyString()))
                .thenThrow(new KnowledgeArchivedException());
        mvc.perform(patch("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE)
                        .header(HttpHeaders.IF_MATCH,"W/\"0\"")
                        .contentType("application/merge-patch+json")
                        .content("{\"title\":\"Blocked\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_ARCHIVED"));

        when(commands.create(eq(PRODUCT),any(),anyString())).thenThrow(new ProductArchivedException());
        mvc.perform(post("/api/products/{p}/knowledge",PRODUCT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeType\":\"FEATURE\",\"title\":\"Title\",\"content\":\"Content\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ARCHIVED"));
    }
    @Test void mapsOwnershipMismatchToKnowledgeNotFound() throws Exception {
        when(queries.get(PRODUCT,KNOWLEDGE)).thenThrow(new KnowledgeNotFoundException());

        mvc.perform(get("/api/products/{p}/knowledge/{k}",PRODUCT,KNOWLEDGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Knowledge not found"));
    }
    private ProductKnowledge value(){return ProductKnowledge.create(KNOWLEDGE,PRODUCT,KnowledgeType.FEATURE,"Title","Content",null);}
}
