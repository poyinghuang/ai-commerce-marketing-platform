package com.aicommerce.platform.creativeplan.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import com.aicommerce.platform.creativeplan.application.*;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.product.application.ProductArchivedException;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.mockito.ArgumentCaptor;
import java.util.List;
import com.aicommerce.platform.common.domain.LifecycleStatus;

@WebMvcTest(CreativePlanController.class)
@Import({CreativePlanMergePatchParser.class,GlobalExceptionHandler.class,RequestIdFilter.class})
class CreativePlanControllerTest {
 static final UUID PRODUCT=UUID.fromString("d4476a19-30ed-48d9-a518-f9b111bd0911");
 static final UUID PLAN=UUID.fromString("79be8758-1f0d-4ca5-bad6-f51aa923cdb9");
 @Autowired MockMvc mvc;
 @MockitoBean CreativePlanCommandService commands;
 @MockitoBean CreativePlanQueryService queries;

 @Test void createReturnsLocationEtagAndAllFields() throws Exception {
  when(commands.create(eq(PRODUCT),any(),anyString())).thenReturn(plan());
  mvc.perform(post("/api/products/{p}/creative-plans",PRODUCT).contentType(MediaType.APPLICATION_JSON).content("""
   {"planName":"Launch","primaryAudience":"Parents","secondaryAudience":"Teens","painPoint":"Time","coreBenefit":"Speed","creativeAngle":"Simple","emotionalDirection":"Hope","brandTone":"Warm","visualStyle":"Clean","mainColor":"Green","characterSetting":"Family","cta":"Buy"}
   """)).andExpect(status().isCreated()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\""))
   .andExpect(header().string(HttpHeaders.LOCATION,"/api/products/"+PRODUCT+"/creative-plans/"+PLAN))
   .andExpect(jsonPath("$.planName").value("Launch"));
 }
 @Test void patchRequiresStrictEtagAndRejectsUnknownOrNullName() throws Exception {
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).contentType("application/merge-patch+json").content("{}"))
   .andExpect(status().isPreconditionRequired());
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"0").contentType("application/merge-patch+json").content("{}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"version\":2}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MERGE_PATCH"));
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{\"planName\":null}"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("planName"));
 }
 @Test void mapsStaleArchivedAndProductArchivedContracts() throws Exception {
  doThrow(new CreativePlanPreconditionFailedException()).when(commands).patch(any(),any(),anyLong(),any(),anyString());
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{}"))
   .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
  reset(commands); doThrow(new CreativePlanArchivedException()).when(commands).patch(any(),any(),anyLong(),any(),anyString());
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_ARCHIVED"));
  reset(commands); doThrow(new ProductArchivedException()).when(commands).patch(any(),any(),anyLong(),any(),anyString());
  mvc.perform(patch("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\"").contentType("application/merge-patch+json").content("{}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_ARCHIVED"));
 }
 @Test void archiveAndRestoreExposeResourceEtag() throws Exception {
  when(commands.archive(any(),any(),anyLong(),anyString())).thenReturn(plan()); when(commands.restore(any(),any(),anyLong(),anyString())).thenReturn(plan());
  mvc.perform(delete("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\""))
   .andExpect(status().isNoContent()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\""));
  mvc.perform(post("/api/products/{p}/creative-plans/{id}/restore",PRODUCT,PLAN).header(HttpHeaders.IF_MATCH,"W/\"0\""))
   .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ETAG,"W/\"0\""));
 }
 @Test void ownerMismatchUsesCreativePlanNotFoundContract() throws Exception {
  when(queries.get(PRODUCT,PLAN)).thenThrow(new CreativePlanNotFoundException());
  mvc.perform(get("/api/products/{p}/creative-plans/{id}",PRODUCT,PLAN)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CREATIVE_PLAN_NOT_FOUND"));
 }
 @Test void listRejectsBadPagingStatusAndSort() throws Exception {
  mvc.perform(get("/api/products/{p}/creative-plans?size=101",PRODUCT)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/products/{p}/creative-plans?status=DELETED",PRODUCT)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/products/{p}/creative-plans?sort=productUuid,asc",PRODUCT)).andExpect(status().isBadRequest());
 }
 @Test void listUsesStatusAndStableUuidSecondarySort() throws Exception {
  when(queries.list(eq(PRODUCT),eq(LifecycleStatus.ACTIVE),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(plan())));
  mvc.perform(get("/api/products/{p}/creative-plans?status=ACTIVE&page=0&size=20&sort=planName,asc",PRODUCT)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].planName").value("Launch"));
  ArgumentCaptor<Pageable> pageable=ArgumentCaptor.forClass(Pageable.class);verify(queries).list(eq(PRODUCT),eq(LifecycleStatus.ACTIVE),pageable.capture());
  assertThat(pageable.getValue().getSort().getOrderFor("planName")).isNotNull();
  assertThat(pageable.getValue().getSort().getOrderFor("creativePlanUuid")).isNotNull();
 }
 private CreativePlan plan(){CreativePlan p=CreativePlan.create(PLAN,PRODUCT,"Launch");p.update("Launch","Parents","Teens","Time","Speed","Simple","Hope","Warm","Clean","Green","Family","Buy");return p;}
}
