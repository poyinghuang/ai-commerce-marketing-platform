package com.aicommerce.platform.creativeplan.application;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import com.aicommerce.platform.audit.application.*;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class CreativePlanCommandServiceTest {
 static final UUID PRODUCT=UUID.randomUUID(),PLAN=UUID.randomUUID(); CreativePlanJpaRepository plans=mock(CreativePlanJpaRepository.class);ProductJpaRepository products=mock(ProductJpaRepository.class);AuditOperationContextFactory contexts=mock(AuditOperationContextFactory.class);AuditWriter writer=mock(AuditWriter.class);Clock clock=Clock.fixed(Instant.parse("2026-08-07T00:00:00Z"),ZoneOffset.UTC);CreativePlanCommandService service=new CreativePlanCommandService(plans,products,contexts,writer,new CreativePlanAuditChangeFactory(),clock);
 @BeforeEach void setup(){when(contexts.forCurrentActor(any())).thenReturn(new AuditOperationContext(UUID.randomUUID(),"request",AuditActor.localAdmin(),AuditSource.API));}
 @Test void patchAuditsOnlyActualChangesAndNoopDoesNothing(){Product product=product();CreativePlan plan=CreativePlan.create(PLAN,PRODUCT,"Launch");when(products.findById(PRODUCT)).thenReturn(Optional.of(product));when(plans.findByCreativePlanUuidAndProductUuid(PLAN,PRODUCT)).thenReturn(Optional.of(plan));when(plans.saveAndFlush(plan)).thenReturn(plan);var absent=FieldPatch.<String>absent();var change=new PatchCreativePlanCommand(absent,FieldPatch.present("Parents"),absent,absent,absent,absent,absent,absent,absent,absent,absent,absent);service.patch(PRODUCT,PLAN,0,change,"request");ArgumentCaptor<AuditEvent> event=ArgumentCaptor.forClass(AuditEvent.class);verify(writer).append(event.capture());assertThat(event.getValue().entityType()).isEqualTo("CREATIVE_PLAN");assertThat(event.getValue().productUuid()).isEqualTo(PRODUCT);assertThat(event.getValue().context().requestId()).isEqualTo("request");assertThat(event.getValue().context().actor()).isEqualTo(AuditActor.localAdmin());assertThat(event.getValue().changes()).extracting(AuditChange::fieldName).containsExactly("primary_audience");clearInvocations(writer,plans);service.patch(PRODUCT,PLAN,0,change,"request");verifyNoInteractions(writer);verify(plans,never()).saveAndFlush(any());}
 @Test void idempotentArchiveDoesNotFlushOrAudit(){CreativePlan plan=CreativePlan.create(PLAN,PRODUCT,"Launch");plan.archive(clock.instant());when(products.findById(PRODUCT)).thenReturn(Optional.of(product()));when(plans.findByCreativePlanUuidAndProductUuid(PLAN,PRODUCT)).thenReturn(Optional.of(plan));service.archive(PRODUCT,PLAN,0,"request");verify(plans,never()).saveAndFlush(any());verifyNoInteractions(writer);}
 @Test void archivedProductBlocksMutationBeforePlanWrite(){Product product=product();product.archive(clock.instant());when(products.findById(PRODUCT)).thenReturn(Optional.of(product));assertThatThrownBy(()->service.create(PRODUCT,new CreateCreativePlanCommand("Launch",null,null,null,null,null,null,null,null,null,null,null),"request")).isInstanceOf(ProductArchivedException.class);verifyNoInteractions(plans,writer);}
 private Product product(){return Product.create(PRODUCT,"PROD-00000001",null,"Product",null,null,null,null,null,null,null,null,null);}
}
