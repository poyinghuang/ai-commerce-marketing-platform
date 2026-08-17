package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import com.aicommerce.platform.ai.application.CreateImageGenerationBatchCommand;
import com.aicommerce.platform.ai.application.CreateTextGenerationBatchCommand;
import com.aicommerce.platform.ai.application.CreatePromptTemplateCommand;
import com.aicommerce.platform.ai.application.AppendPromptTemplateVersionCommand;
import com.aicommerce.platform.ai.application.AiPromptTemplateService;
import com.aicommerce.platform.ai.application.TextGenerationService;
import com.aicommerce.platform.ai.application.ImageGenerationService;
import com.aicommerce.platform.ai.application.ReviewDecisionService;
import com.aicommerce.platform.ai.domain.GenerationType;
import com.aicommerce.platform.ai.infrastructure.provider.LocalImagePromptBootstrap;
import com.aicommerce.platform.ai.infrastructure.provider.StubAssetBinaryStore;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class Milestone4AAdEvidenceAcceptanceIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @DynamicPropertySource static void budget(DynamicPropertyRegistry r){r.add("AI_BUDGET_CURRENCY",()->"USD");r.add("AI_MAX_JOB_COST",()->"10.000000");r.add("AI_MAX_BATCH_COST",()->"10.000000");r.add("AI_MAX_DAILY_COST",()->"1000.000000");}
 @Autowired JdbcTemplate jdbc; @Autowired ImageGenerationService images; @Autowired ReviewDecisionService reviews;
 @Autowired TextGenerationService texts; @Autowired AiPromptTemplateService templates; @Autowired AuditOperationContextFactory contexts; @Autowired PlatformTransactionManager transactionManager;

 @Test void adRequiresExactActiveProductGeneratedImageApprovalPreservationAndChecksumSnapshot(){
  ImageFixture valid=imageFixture(true); PlatformFixture platform=platformFixture();
  UUID ad=insertAd(platform,valid.product(),valid.generatedAsset(),valid.output(),valid.decision(),valid.checksum());
  assertThat(jdbc.queryForObject("select count(*) from platform_ads where platform_ad_uuid=?",Integer.class,ad)).isOne();
  assertThatThrownBy(()->jdbc.update("update platform_ads set approved_checksum_sha256=? where platform_ad_uuid=?","f".repeat(64),ad)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_ads where platform_ad_uuid=?",ad)).isInstanceOf(RuntimeException.class);

  assertAdRejected(platform,valid.product(),valid.sourceAsset(),valid.output(),valid.decision(),valid.checksum());
  assertAdRejected(platform,valid.product(),valid.generatedAsset(),valid.output(),valid.decision(),"e".repeat(64));
  assertAdRejected(platform,UUID.randomUUID(),valid.generatedAsset(),valid.output(),valid.decision(),valid.checksum());
  ImageFixture rejected=imageFixture(false);
  assertAdRejected(platform,rejected.product(),rejected.generatedAsset(),rejected.output(),rejected.decision(),rejected.checksum());

  jdbc.update("update products set lifecycle_status='ARCHIVED',archived_at=current_timestamp,updated_at=current_timestamp,version=version+1 where product_uuid=?",valid.product());
  assertThat(jdbc.queryForObject("select approved_checksum_sha256 from platform_ads where platform_ad_uuid=?",String.class,ad).trim()).isEqualTo(valid.checksum());
  assertAdRejected(platform,valid.product(),valid.generatedAsset(),valid.output(),valid.decision(),valid.checksum());
 }

 @Test void productAssetOutputReviewAndPreservationFailuresExposeExactSqlStateAndLeaveNoAd(){
  PlatformFixture platform=platformFixture(); ImageFixture first=imageFixture(true),second=imageFixture(true);
  assertAdSqlState(platform,first.product(),second.generatedAsset(),first.output(),first.decision(),first.checksum(),"23503");
  assertAdSqlState(platform,first.product(),first.generatedAsset(),second.output(),second.decision(),second.checksum(),"23503");
  assertAdSqlState(platform,first.product(),first.generatedAsset(),first.output(),UUID.randomUUID(),first.checksum(),"23503");
  ImageFixture rejected=imageFixture(false);
  assertAdSqlState(platform,rejected.product(),rejected.generatedAsset(),rejected.output(),rejected.decision(),rejected.checksum(),"23514");

  ImageFixture pending=imageFixture(null);
  assertSyntheticDecisionRejected(platform,pending,false);
  ImageFixture blocked=imageFixture("BLOCKED");
  assertSyntheticDecisionRejected(platform,blocked,true);

  ImageFixture text=textFixture();
  assertAdSqlState(platform,text.product(),text.generatedAsset(),text.output(),text.decision(),text.checksum(),"23514");
 }

 @Test void inactiveNonImageNullChecksumAndLaterAssetDivergenceKeepHistoricalSnapshotButBlockNewAd(){
  PlatformFixture platform=platformFixture();
  for(String divergence:java.util.List.of("ARCHIVED","VIDEO","NULL_CHECKSUM")){
   ImageFixture fixture=imageFixture(true); UUID historical=insertAd(platform,fixture.product(),fixture.generatedAsset(),fixture.output(),fixture.decision(),fixture.checksum());
   if("ARCHIVED".equals(divergence))jdbc.update("update assets set lifecycle_status='ARCHIVED',archived_at=current_timestamp,updated_at=current_timestamp,version=version+1 where asset_uuid=?",fixture.generatedAsset());
   else if("VIDEO".equals(divergence))jdbc.update("update assets set asset_type='VIDEO',updated_at=current_timestamp,version=version+1 where asset_uuid=?",fixture.generatedAsset());
   else jdbc.update("update assets set checksum_sha256=null,updated_at=current_timestamp,version=version+1 where asset_uuid=?",fixture.generatedAsset());
   assertThat(jdbc.queryForMap("select product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256 from platform_ads where platform_ad_uuid=?",historical))
     .containsEntry("product_uuid",fixture.product()).containsEntry("asset_uuid",fixture.generatedAsset()).containsEntry("generation_output_uuid",fixture.output()).containsEntry("review_decision_uuid",fixture.decision()).containsEntry("approved_checksum_sha256",fixture.checksum());
   assertAdSqlState(platform,fixture.product(),fixture.generatedAsset(),fixture.output(),fixture.decision(),fixture.checksum(),"23514");
  }
 }

 private void assertAdRejected(PlatformFixture p,UUID product,UUID asset,UUID output,UUID decision,String checksum){assertThatThrownBy(()->insertAd(p,product,asset,output,decision,checksum)).isInstanceOf(RuntimeException.class);}
 private void assertAdSqlState(PlatformFixture p,UUID product,UUID asset,UUID output,UUID decision,String checksum,String state){int before=adCount();assertThatThrownBy(()->insertAd(p,product,asset,output,decision,checksum)).isInstanceOf(DataAccessException.class).satisfies(error->assertThat(sqlState(error)).isEqualTo(state));assertThat(adCount()).isEqualTo(before);}
 private void assertSyntheticDecisionRejected(PlatformFixture p,ImageFixture fixture,boolean approveOutput){int before=adCount();assertThatThrownBy(()->new TransactionTemplate(transactionManager).executeWithoutResult(status->{UUID decision=UUID.randomUUID();jdbc.update("insert into ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) values (?,?,'APPROVED','LOCAL_ADMIN','stage4a','stage4a-synthetic',0,current_timestamp)",decision,fixture.output());if(approveOutput)jdbc.update("update ai_generation_outputs set review_status='APPROVED',updated_at=current_timestamp,version=1 where generation_output_uuid=?",fixture.output());insertAd(p,fixture.product(),fixture.generatedAsset(),fixture.output(),decision,fixture.checksum());jdbc.execute("set constraints trg_platform_ad_evidence_snapshot immediate");})).isInstanceOf(DataAccessException.class).satisfies(error->assertThat(sqlState(error)).isEqualTo("23514"));assertThat(adCount()).isEqualTo(before);assertThat(jdbc.queryForObject("select review_status from ai_generation_outputs where generation_output_uuid=?",String.class,fixture.output())).isEqualTo("PENDING_REVIEW");}
 private int adCount(){return jdbc.queryForObject("select count(*) from platform_ads",Integer.class);}
 private String sqlState(Throwable error){Throwable current=error;while(current!=null){if(current instanceof SQLException sql)return sql.getSQLState();current=current.getCause();}throw new AssertionError("missing SQLException",error);}
 private UUID insertAd(PlatformFixture p,UUID product,UUID asset,UUID output,UUID decision,String checksum){UUID ad=UUID.randomUUID();jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?,?)",ad,p.adSet(),p.account(),product,asset,output,decision,checksum,"IMAGE_PRIMARY_V1");return ad;}
 private PlatformFixture platformFixture(){UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),adSet=UUID.randomUUID();jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"ad-"+account,account.toString().replace("-","").repeat(2));jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Ad Evidence')",plan);jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'DAILY',50,'TWD','Asia/Taipei','SALES','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1')",adSet,campaign,account);return new PlatformFixture(account,adSet);}
 private ImageFixture imageFixture(boolean approve){return imageFixture(approve?"APPROVED":"REJECTED");}
 private ImageFixture imageFixture(String review){UUID product=UUID.randomUUID(),creative=UUID.randomUUID(),source=UUID.randomUUID();jdbc.update("insert into products(product_uuid,product_id,product_name,lifecycle_status,version) values (?,?,?,'ACTIVE',0)",product,"PROD-"+String.format("%08d",Math.abs(product.hashCode())%100000000),"Ad Product");jdbc.update("insert into creative_plans(creative_plan_uuid,product_uuid,plan_name,visual_style) values (?,?,'Ad Plan','Clean')",creative,product);byte[] bytes=StubAssetBinaryStore.fixture();String checksum=StubAssetBinaryStore.sha256(bytes);String handle="BLOCKED".equals(review)?StubAssetBinaryStore.CHANGED_PIXEL_SOURCE_HANDLE:StubAssetBinaryStore.SOURCE_HANDLE;jdbc.update("insert into assets(asset_uuid,product_uuid,creative_plan_uuid,asset_type,purpose,storage_provider,provider_file_id,media_type,original_filename,size_bytes,checksum_sha256) values (?,?,?,'IMAGE','PRODUCT_SOURCE','LOCAL_STUB',?,'image/png','source.png',?,?)",source,product,creative,handle,bytes.length,checksum);var batch=images.createBatch(new CreateImageGenerationBatchCommand(product,creative,LocalImagePromptBootstrap.TEMPLATE_KEY,ImageGenerationService.WORKFLOW_KEY,"STANDARD_IMAGE",source,null),"ad-image-create-"+product);var output=images.execute(batch.jobs().getFirst().getGenerationJobUuid(),batch.jobs().getFirst().getVersion(),"ad-image-execute-"+product);if("APPROVED".equals(review))reviews.approve(output.getGenerationOutputUuid(),output.getVersion(),"ad-review-"+product);else if("REJECTED".equals(review))reviews.reject(output.getGenerationOutputUuid(),output.getVersion(),"rejected fixture","ad-review-"+product);UUID decision=review==null||"BLOCKED".equals(review)?null:jdbc.queryForObject("select review_decision_uuid from ai_review_decisions where generation_output_uuid=?",UUID.class,output.getGenerationOutputUuid());String outputChecksum=jdbc.queryForObject("select output_checksum_sha256 from ai_generation_outputs where generation_output_uuid=?",String.class,output.getGenerationOutputUuid());return new ImageFixture(product,source,output.getGeneratedAssetUuid(),output.getGenerationOutputUuid(),decision,outputChecksum);}
 private ImageFixture textFixture(){UUID product=UUID.randomUUID(),creative=UUID.randomUUID(),asset=UUID.randomUUID();String key="stage4a.text."+product.toString().substring(0,8);jdbc.update("insert into products(product_uuid,product_id,product_name,lifecycle_status,version) values (?,?,?,'ACTIVE',0)",product,"PROD-"+String.format("%08d",Math.abs(product.hashCode())%100000000),"Text Ad Product");jdbc.update("insert into creative_plans(creative_plan_uuid,product_uuid,plan_name,visual_style) values (?,?,'Text Ad Plan','Clean')",creative,product);jdbc.update("insert into product_knowledge(knowledge_uuid,product_uuid,knowledge_type,title,content) values (?,?,'FEATURE','Feature','Evidence')",UUID.randomUUID(),product);var context=contexts.forSystem("stage4a-text");var template=templates.createTemplate(new CreatePromptTemplateCommand(key,GenerationType.TEXT,"Stage4A text"),context);templates.appendVersion(template.getPromptTemplateUuid(),new AppendPromptTemplateVersionCommand("Write text",null,"{\"type\":\"object\",\"properties\":{\"product\":{},\"knowledge\":{},\"creativePlan\":{},\"variationIndex\":{}}}"),context);var batch=texts.createBatch(new CreateTextGenerationBatchCommand(product,creative,key,"STANDARD",1),"stage4a-text-create-"+product);var output=texts.execute(batch.jobs().getFirst().getGenerationJobUuid(),0,"stage4a-text-execute-"+product);reviews.approve(output.getGenerationOutputUuid(),output.getVersion(),"stage4a-text-review-"+product);byte[] bytes=StubAssetBinaryStore.fixture();String checksum=StubAssetBinaryStore.sha256(bytes);jdbc.update("insert into assets(asset_uuid,product_uuid,creative_plan_uuid,asset_type,purpose,storage_provider,provider_file_id,media_type,original_filename,size_bytes,checksum_sha256) values (?,?,?,'IMAGE','AI_BACKGROUND_COMPOSITE','LOCAL_STUB',?,'image/png','text.png',?,?)",asset,product,creative,"text-"+asset,bytes.length,checksum);UUID decision=jdbc.queryForObject("select review_decision_uuid from ai_review_decisions where generation_output_uuid=?",UUID.class,output.getGenerationOutputUuid());return new ImageFixture(product,asset,asset,output.getGenerationOutputUuid(),decision,checksum);}
 private record PlatformFixture(UUID account,UUID adSet){} private record ImageFixture(UUID product,UUID sourceAsset,UUID generatedAsset,UUID output,UUID decision,String checksum){}
}
