package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import com.aicommerce.platform.ai.application.CreateImageGenerationBatchCommand;
import com.aicommerce.platform.ai.application.ImageGenerationService;
import com.aicommerce.platform.ai.application.ReviewDecisionService;
import com.aicommerce.platform.ai.infrastructure.provider.LocalImagePromptBootstrap;
import com.aicommerce.platform.ai.infrastructure.provider.StubAssetBinaryStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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

 private void assertAdRejected(PlatformFixture p,UUID product,UUID asset,UUID output,UUID decision,String checksum){assertThatThrownBy(()->insertAd(p,product,asset,output,decision,checksum)).isInstanceOf(RuntimeException.class);}
 private UUID insertAd(PlatformFixture p,UUID product,UUID asset,UUID output,UUID decision,String checksum){UUID ad=UUID.randomUUID();jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?,?)",ad,p.adSet(),p.account(),product,asset,output,decision,checksum,"IMAGE_PRIMARY_V1");return ad;}
 private PlatformFixture platformFixture(){UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),adSet=UUID.randomUUID();jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"ad-"+account,account.toString().replace("-","").repeat(2));jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Ad Evidence')",plan);jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'DAILY',50,'TWD','Asia/Taipei','SALES','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1')",adSet,campaign,account);return new PlatformFixture(account,adSet);}
 private ImageFixture imageFixture(boolean approve){UUID product=UUID.randomUUID(),creative=UUID.randomUUID(),source=UUID.randomUUID();jdbc.update("insert into products(product_uuid,product_id,product_name,lifecycle_status,version) values (?,?,?,'ACTIVE',0)",product,"PROD-"+String.format("%08d",Math.abs(product.hashCode())%100000000),"Ad Product");jdbc.update("insert into creative_plans(creative_plan_uuid,product_uuid,plan_name,visual_style) values (?,?,'Ad Plan','Clean')",creative,product);byte[] bytes=StubAssetBinaryStore.fixture();String checksum=StubAssetBinaryStore.sha256(bytes);jdbc.update("insert into assets(asset_uuid,product_uuid,creative_plan_uuid,asset_type,purpose,storage_provider,provider_file_id,media_type,original_filename,size_bytes,checksum_sha256) values (?,?,?,'IMAGE','PRODUCT_SOURCE','LOCAL_STUB',?,'image/png','source.png',?,?)",source,product,creative,StubAssetBinaryStore.SOURCE_HANDLE,bytes.length,checksum);var batch=images.createBatch(new CreateImageGenerationBatchCommand(product,creative,LocalImagePromptBootstrap.TEMPLATE_KEY,ImageGenerationService.WORKFLOW_KEY,"STANDARD_IMAGE",source,null),"ad-image-create-"+product);var output=images.execute(batch.jobs().getFirst().getGenerationJobUuid(),batch.jobs().getFirst().getVersion(),"ad-image-execute-"+product);if(approve)reviews.approve(output.getGenerationOutputUuid(),output.getVersion(),"ad-review-"+product);else reviews.reject(output.getGenerationOutputUuid(),output.getVersion(),"rejected fixture","ad-review-"+product);UUID decision=jdbc.queryForObject("select review_decision_uuid from ai_review_decisions where generation_output_uuid=?",UUID.class,output.getGenerationOutputUuid());String outputChecksum=jdbc.queryForObject("select output_checksum_sha256 from ai_generation_outputs where generation_output_uuid=?",String.class,output.getGenerationOutputUuid());return new ImageFixture(product,source,output.getGeneratedAssetUuid(),output.getGenerationOutputUuid(),decision,outputChecksum);}
 private record PlatformFixture(UUID account,UUID adSet){} private record ImageFixture(UUID product,UUID sourceAsset,UUID generatedAsset,UUID output,UUID decision,String checksum){}
}
