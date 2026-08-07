package com.aicommerce.platform.campaign.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.campaign.domain.*;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.GlobalExceptionHandler;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({CampaignController.class, CampaignProductController.class})
@Import({
  CampaignMergePatchParser.class,
  CampaignProductMergePatchParser.class,
  GlobalExceptionHandler.class,
  RequestIdFilter.class
})
class CampaignControllerTest {
  static final UUID CAMPAIGN = UUID.fromString("22d9f86d-1d4e-4bbb-9e86-5d252624ea79"),
      PRODUCT = UUID.fromString("98597164-a94c-4ac3-a47a-f6911b49681f"),
      RELATION = UUID.fromString("715561c4-2e66-48b4-96bb-c565726fe348");
  @Autowired MockMvc mvc;
  @MockitoBean CampaignCommandService commands;
  @MockitoBean CampaignQueryService queries;

  @Test
  void campaignEndpointsPreserveLocationEtagAndPreconditions() throws Exception {
    CampaignPlan c = CampaignPlan.create(CAMPAIGN, "Launch");
    when(commands.create(any(), anyString())).thenReturn(c);
    when(queries.get(CAMPAIGN)).thenReturn(c);
    when(commands.patch(eq(CAMPAIGN), eq(0L), any(), anyString())).thenReturn(c);
    when(commands.archive(eq(CAMPAIGN), eq(0L), anyString())).thenReturn(c);
    when(commands.restore(eq(CAMPAIGN), eq(0L), anyString())).thenReturn(c);
    mvc.perform(
            post("/api/campaigns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campaignName\":\"Launch\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/campaigns/" + CAMPAIGN))
        .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""));
    mvc.perform(get("/api/campaigns/{id}", CAMPAIGN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.campaignName").value("Launch"));
    mvc.perform(
            patch("/api/campaigns/{id}", CAMPAIGN)
                .contentType("application/merge-patch+json")
                .content("{}"))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
    mvc.perform(
            patch("/api/campaigns/{id}", CAMPAIGN)
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType("application/merge-patch+json")
                .content("{\"objective\":\"Sales\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ETAG, "W/\"0\""));
    mvc.perform(delete("/api/campaigns/{id}", CAMPAIGN).header(HttpHeaders.IF_MATCH, "W/\"0\""))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/campaigns/{id}/restore", CAMPAIGN).header(HttpHeaders.IF_MATCH, "W/\"0\""))
        .andExpect(status().isOk());
  }

  @Test
  void listValidatesAllowlistAndUsesStablePage() throws Exception {
    when(queries.list(any(), any(), any(), any(), any()))
        .thenReturn(
            new PageImpl<>(
                List.of(CampaignPlan.create(CAMPAIGN, "Launch")), PageRequest.of(0, 20), 1));
    mvc.perform(get("/api/campaigns?sort=campaignName,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].campaignUuid").value(CAMPAIGN.toString()));
    mvc.perform(get("/api/campaigns?sort=secret,asc")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/campaigns?size=101")).andExpect(status().isBadRequest());
  }

  @Test
  void associationEndpointsUseProductIdentityAndEtag() throws Exception {
    CampaignProduct cp = CampaignProduct.create(RELATION, CAMPAIGN, PRODUCT);
    when(commands.addProduct(eq(CAMPAIGN), any(), anyString())).thenReturn(cp);
    when(queries.getProduct(CAMPAIGN, PRODUCT)).thenReturn(cp);
    when(commands.patchProduct(eq(CAMPAIGN), eq(PRODUCT), eq(0L), any(), anyString()))
        .thenReturn(cp);
    when(commands.archiveProduct(eq(CAMPAIGN), eq(PRODUCT), eq(0L), anyString())).thenReturn(cp);
    when(commands.restoreProduct(eq(CAMPAIGN), eq(PRODUCT), eq(0L), anyString())).thenReturn(cp);
    mvc.perform(
            post("/api/campaigns/{c}/products", CAMPAIGN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productUuid\":\"" + PRODUCT + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string(
                    HttpHeaders.LOCATION, "/api/campaigns/" + CAMPAIGN + "/products/" + PRODUCT));
    mvc.perform(get("/api/campaigns/{c}/products/{p}", CAMPAIGN, PRODUCT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.campaignProductUuid").value(RELATION.toString()));
    mvc.perform(
            patch("/api/campaigns/{c}/products/{p}", CAMPAIGN, PRODUCT)
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType("application/merge-patch+json")
                .content("{\"role\":\"Hero\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            delete("/api/campaigns/{c}/products/{p}", CAMPAIGN, PRODUCT)
                .header(HttpHeaders.IF_MATCH, "W/\"0\""))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/campaigns/{c}/products/{p}/restore", CAMPAIGN, PRODUCT)
                .header(HttpHeaders.IF_MATCH, "W/\"0\""))
        .andExpect(status().isOk());
  }

  @Test
  void domainErrorsMapToStableCodes() throws Exception {
    when(queries.get(CAMPAIGN)).thenThrow(new CampaignNotFoundException());
    mvc.perform(get("/api/campaigns/{id}", CAMPAIGN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
    when(commands.addProduct(eq(CAMPAIGN), any(), anyString()))
        .thenThrow(new RelationshipConflictException());
    mvc.perform(
            post("/api/campaigns/{c}/products", CAMPAIGN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productUuid\":\"" + PRODUCT + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RELATIONSHIP_CONFLICT"));
  }
}
