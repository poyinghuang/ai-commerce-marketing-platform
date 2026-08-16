package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.boot.testcontainers.service.connection.ServiceConnection; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.test.context.ActiveProfiles; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers; import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test") class Milestone4ASchemaIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired JdbcTemplate jdbc;
 @Test void createsAllFoundationTablesWithNumericMoney(){
  assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public' and table_name like 'platform_%'",String.class)).contains("platform_accounts","platform_campaigns","platform_ad_sets","platform_ads","platform_operations","platform_metric_snapshots");
  assertThat(jdbc.queryForObject("select numeric_scale from information_schema.columns where table_name='platform_ad_sets' and column_name='budget_amount'",Integer.class)).isEqualTo(6);
  assertThat(jdbc.queryForList("select numeric_scale from information_schema.columns where table_name='platform_metric_snapshots' and column_name in ('spend','revenue') order by column_name",Integer.class)).containsExactly(6,6);
 }
 @Test void operationInputAndTerminalRowsCannotBeChangedOrDeleted(){
  UUID account=account(); UUID plan=UUID.randomUUID(); UUID campaign=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'4A')",plan);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  UUID operation=UUID.randomUUID();
  jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, '{}'::jsonb,?,'LOCAL_ADMIN','tester','request-4a',3)",operation,account,campaign,UUID.randomUUID(),"a".repeat(64),"b".repeat(64));
  assertThatThrownBy(()->jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, '{}'::jsonb,?,'LOCAL_ADMIN','other','request-duplicate',3)",UUID.randomUUID(),account,campaign,UUID.randomUUID(),"a".repeat(64),"b".repeat(64))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_operations set request_payload='{\"changed\":true}' where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=current_timestamp,version=1 where operation_uuid=?",operation);
  jdbc.update("update platform_operations set status='SUCCEEDED',external_id='fake-1',completed_at=current_timestamp,version=2 where operation_uuid=?",operation);
  assertThatThrownBy(()->jdbc.update("update platform_operations set status='FAILED_TERMINAL' where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_operations where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_campaigns where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_accounts where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  UUID unknown=UUID.randomUUID();
  jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, '{}'::jsonb,?,'LOCAL_ADMIN','tester','request-unknown',3)",unknown,account,campaign,UUID.randomUUID(),"d".repeat(64),"e".repeat(64));
  jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=current_timestamp,version=1 where operation_uuid=?",unknown);
  jdbc.update("update platform_operations set status='UNKNOWN_OUTCOME',version=2 where operation_uuid=?",unknown);
  assertThatThrownBy(()->jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=2,claimed_at=current_timestamp,version=3 where operation_uuid=?",unknown)).isInstanceOf(RuntimeException.class);
  UUID snapshot=UUID.randomUUID();
  jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,fetched_at,freshness_status,source_fingerprint) values (?,?,'CAMPAIGN',?,current_timestamp-interval '1 day',current_timestamp,'Asia/Taipei','TWD',current_timestamp,'FRESH',?)",snapshot,account,campaign,"f".repeat(64));
  assertThatThrownBy(()->jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,fetched_at,freshness_status,source_fingerprint) select ?,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,current_timestamp,freshness_status,? from platform_metric_snapshots where metric_snapshot_uuid=?",UUID.randomUUID(),"0".repeat(64),snapshot)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_metric_snapshots set freshness_status='DELAYED' where metric_snapshot_uuid=?",snapshot)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_metric_snapshots where metric_snapshot_uuid=?",snapshot)).isInstanceOf(RuntimeException.class);
 }
 @Test void resourceOwnershipIdentityEvidenceAndDeleteRulesAreDatabaseEnforced(){
  UUID account=account(), plan=UUID.randomUUID(), campaign=UUID.randomUUID(), adSet=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Resources')",plan);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'LIFETIME',300.000000,'TWD','Asia/Taipei','SALES','TW_BROAD_FEEDS_V1','FEEDS_V1')",adSet,campaign,account);
  assertThatThrownBy(()->jdbc.update("update platform_accounts set currency='USD' where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_campaigns set campaign_uuid=? where platform_campaign_uuid=?",UUID.randomUUID(),campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_ad_sets set platform_campaign_uuid=? where platform_ad_set_uuid=?",UUID.randomUUID(),adSet)).isInstanceOf(RuntimeException.class);
  UUID product=UUID.randomUUID(), asset=UUID.randomUUID(), template=UUID.randomUUID(), templateVersion=UUID.randomUUID(), batch=UUID.randomUUID(), job=UUID.randomUUID(), output=UUID.randomUUID(), decision=UUID.randomUUID(), ad=UUID.randomUUID();
  jdbc.update("insert into products(product_uuid,product_id,product_name,lifecycle_status,version) values (?,'PROD-00009412','Evidence','ACTIVE',0)",product);
  jdbc.update("insert into assets(asset_uuid,product_uuid,asset_type,checksum_sha256) values (?,?,'IMAGE',?)",asset,product,"1".repeat(64));
  jdbc.update("insert into ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) values (?,'schema.evidence','TEXT','Evidence')",template);
  jdbc.update("insert into ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) values (?,?,1,'x','{}'::jsonb,?,'tester')",templateVersion,template,"2".repeat(64));
  jdbc.update("insert into ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) values (?,?,'COMPLETED','TWD',0,0,1,1,'tester')",batch,product);
  jdbc.update("insert into ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) values (?,?,?,?,'TEXT','stub','stub','SUCCEEDED','x','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)",job,batch,product,templateVersion);
  jdbc.update("insert into ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,text_content,model_label,input_units,output_units,actual_cost,currency) values (?,?,?,?,'TEXT','x','stub',1,1,0,'TWD')",output,job,batch,product);
  jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c->{boolean auto=c.getAutoCommit();c.setAutoCommit(false);try(var d=c.prepareStatement("insert into ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) values (?,?,'APPROVED','LOCAL_ADMIN','tester','schema-evidence',0,current_timestamp)");var o=c.prepareStatement("update ai_generation_outputs set review_status='APPROVED',version=1 where generation_output_uuid=?")){d.setObject(1,decision);d.setObject(2,output);d.executeUpdate();o.setObject(1,output);o.executeUpdate();c.commit();}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(auto);}return null;});
  jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?, 'mapping-v1')",ad,adSet,account,product,asset,output,decision,"1".repeat(64));
  assertThatThrownBy(()->jdbc.update("update platform_ads set approved_checksum_sha256=? where platform_ad_uuid=?","3".repeat(64),ad)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?, 'bad-owner')",UUID.randomUUID(),adSet,account,UUID.randomUUID(),asset,output,decision,"1".repeat(64))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_ads where platform_ad_uuid=?",ad)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_ad_sets where platform_ad_set_uuid=?",adSet)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_campaigns where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_accounts where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from assets where asset_uuid=?",asset)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'LIFETIME',99999999999999.000000,'TWD','Asia/Taipei','SALES','T','P')",UUID.randomUUID(),campaign,account)).isInstanceOf(RuntimeException.class);
 }
 private UUID account(){UUID id=UUID.randomUUID();String fingerprint=id.toString().replace("-","")+"c".repeat(32);jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",id,"acct-"+id,fingerprint);return id;}
}
