package com.aicommerce.platform.asset.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.aicommerce.platform.asset.application.*;
import com.aicommerce.platform.asset.domain.*;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(AssetController.class)
@Import({AssetMergePatchParser.class,GlobalExceptionHandler.class,RequestIdFilter.class})
class AssetControllerTest {
    static final UUID PRODUCT=UUID.fromString("d4476a19-30ed-48d9-a518-f9b111bd0911");
    static final UUID ASSET=UUID.fromString("79be8758-1f0d-4ca5-bad6-f51aa923cdb9");
    @Autowired MockMvc mvc; @MockitoBean AssetCommandService commands; @MockitoBean AssetQueryService queries;
    @Test void createReturnsLocationEtagAndMetadata() throws Exception {
        when(commands.create(eq(PRODUCT),any(),anyString())).thenReturn(asset());
        mvc.perform(post("/api/products/{p}/assets",PRODUCT).contentType(MediaType.APPLICATION_JSON).content("""
          {"assetType":"IMAGE","fileUrl":"https://cdn.example/a.jpg","providerMetadata":{"region":"eu"}}
          """)).andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\""))
          .andExpect(header().string(HttpHeaders.LOCATION,"/api/products/"+PRODUCT+"/assets/"+ASSET))
          .andExpect(jsonPath("$.providerMetadata.region").value("eu"));
    }
    @Test void patchUsesStrictEtagAndRejectsImmutableOrInvalidMetadata() throws Exception {
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).contentType("application/merge-patch+json").content("{}"))
          .andExpect(status().isPreconditionRequired());
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).header(HttpHeaders.IF_MATCH,"0").contentType("application/merge-patch+json").content("{}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"campaignUuid\":null}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MERGE_PATCH"));
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"providerMetadata\":[]}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("providerMetadata"));
    }
    @Test void createRejectsNonObjectProviderMetadataWithFieldError() throws Exception {
        mvc.perform(post("/api/products/{p}/assets",PRODUCT).contentType(MediaType.APPLICATION_JSON)
          .content("{\"assetType\":\"IMAGE\",\"providerMetadata\":[\"not-an-object\"]}"))
          .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("providerMetadata"))
          .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-an-object"))));
    }
    @Test void mapsAssetContractsWithoutLeakingMetadata() throws Exception {
        doThrow(new AssetRelationshipConflictException()).when(commands).patch(any(),any(),anyLong(),any(),anyString());
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{}"))
          .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RELATIONSHIP_CONFLICT"));
        reset(commands); doThrow(new AssetPreconditionFailedException()).when(commands).patch(any(),any(),anyLong(),any(),anyString());
        mvc.perform(patch("/api/products/{p}/assets/{id}",PRODUCT,ASSET).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{}"))
          .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }
    @Test void listValidatesFiltersAndUsesStableSecondaryOrdering() throws Exception {
        mvc.perform(get("/api/products/{p}/assets?size=101",PRODUCT)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/{p}/assets?assetType=AUDIO",PRODUCT)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/{p}/assets?sort=providerMetadata,asc",PRODUCT)).andExpect(status().isBadRequest());
        when(queries.list(eq(PRODUCT),eq(LifecycleStatus.ACTIVE),eq(AssetType.IMAGE),isNull(),isNull(),eq("s3"),any()))
          .thenReturn(new PageImpl<>(List.of(asset())));
        mvc.perform(get("/api/products/{p}/assets?assetType=IMAGE&storageProvider=s3&sort=createdAt,asc",PRODUCT)).andExpect(status().isOk());
        ArgumentCaptor<Pageable> page=ArgumentCaptor.forClass(Pageable.class);
        verify(queries).list(eq(PRODUCT),eq(LifecycleStatus.ACTIVE),eq(AssetType.IMAGE),isNull(),isNull(),eq("s3"),page.capture());
        assertThat(page.getValue().getSort().getOrderFor("assetUuid")).isNotNull();
    }
    private Asset asset(){Asset a=Asset.create(ASSET,PRODUCT,null,null,AssetType.IMAGE);a.update(AssetType.IMAGE,null,null,null,"https://cdn.example/a.jpg",null,"a.jpg",1L,null,Map.of("region","eu"));return a;}
}
