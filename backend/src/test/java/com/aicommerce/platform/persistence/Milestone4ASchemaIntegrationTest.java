package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.boot.testcontainers.service.connection.ServiceConnection; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.test.context.ActiveProfiles; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers; import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test") class Milestone4ASchemaIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired JdbcTemplate jdbc;
 @Test void createsAllFoundationTablesWithNumericMoney(){
  assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public' and table_name like 'platform_%'",String.class)).contains("platform_accounts","platform_campaigns","platform_ad_sets","platform_ads","platform_operations","platform_operation_attempts","platform_metric_snapshots");
  assertThat(jdbc.queryForObject("select numeric_scale from information_schema.columns where table_name='platform_ad_sets' and column_name='budget_amount'",Integer.class)).isEqualTo(6);
  assertThat(jdbc.queryForList("select numeric_scale from information_schema.columns where table_name='platform_metric_snapshots' and column_name in ('spend','revenue') order by column_name",Integer.class)).containsExactly(6,6);
 }
 @Test void operationInputAndTerminalRowsCannotBeChangedOrDeleted(){
  UUID account=account(); UUID plan=UUID.randomUUID(); UUID campaign=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'4A')",plan);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  UUID operation=UUID.randomUUID();
  String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
  jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, ?::jsonb,?,'LOCAL_ADMIN','tester','request-4a',3)",operation,account,campaign,UUID.randomUUID(),"a".repeat(64),payload,"b".repeat(64));
  assertThatThrownBy(()->jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, ?::jsonb,?,'LOCAL_ADMIN','other','request-duplicate',3)",UUID.randomUUID(),account,campaign,UUID.randomUUID(),"a".repeat(64),payload,"b".repeat(64))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_operations set request_payload='{\"changed\":true}' where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=current_timestamp,version=1 where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_operations where operation_uuid=?",operation)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_campaigns where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_accounts where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  UUID snapshot=UUID.randomUUID();
  jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?,'CAMPAIGN',?,current_timestamp-interval '1 day',current_timestamp,'Asia/Taipei','TWD',1,current_timestamp,'FRESH',?)",snapshot,account,campaign,"f".repeat(64));
  assertThatThrownBy(()->jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,revision_number,fetched_at,freshness_status,source_fingerprint) select ?,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,3,current_timestamp+interval '1 minute',freshness_status,? from platform_metric_snapshots where metric_snapshot_uuid=?",UUID.randomUUID(),"0".repeat(64),snapshot)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_metric_snapshots set freshness_status='DELAYED' where metric_snapshot_uuid=?",snapshot)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_metric_snapshots where metric_snapshot_uuid=?",snapshot)).isInstanceOf(RuntimeException.class);
 }
 @Test void resourceOwnershipIdentityEvidenceAndDeleteRulesAreDatabaseEnforced(){
  UUID account=account(), plan=UUID.randomUUID(), campaign=UUID.randomUUID(), adSet=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Resources')",plan);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'LIFETIME',300.000000,'TWD','Asia/Taipei','SALES','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1')",adSet,campaign,account);
  assertThatThrownBy(()->jdbc.update("update platform_accounts set currency='USD' where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_campaigns set campaign_uuid=? where platform_campaign_uuid=?",UUID.randomUUID(),campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_ad_sets set platform_campaign_uuid=? where platform_ad_set_uuid=?",UUID.randomUUID(),adSet)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("insert into platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) values (?,?,?,?,?,?,?,?, 'bad-owner')",UUID.randomUUID(),adSet,account,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"1".repeat(64))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_ad_sets where platform_ad_set_uuid=?",adSet)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_campaigns where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_accounts where platform_account_uuid=?",account)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'LIFETIME',99999999999999.000000,'TWD','Asia/Taipei','SALES','T','P')",UUID.randomUUID(),campaign,account)).isInstanceOf(RuntimeException.class);
 }
 private UUID account(){UUID id=UUID.randomUUID();String fingerprint=id.toString().replace("-","")+"c".repeat(32);jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",id,"acct-"+id,fingerprint);return id;}
}
