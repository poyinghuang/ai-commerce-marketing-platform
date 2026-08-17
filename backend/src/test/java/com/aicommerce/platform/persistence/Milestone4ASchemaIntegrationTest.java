package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.boot.testcontainers.service.connection.ServiceConnection; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.test.context.ActiveProfiles; import org.testcontainers.junit.jupiter.Container; import org.testcontainers.junit.jupiter.Testcontainers; import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.transaction.PlatformTransactionManager; import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.sql.Timestamp;

@Testcontainers @SpringBootTest @ActiveProfiles("test") class Milestone4ASchemaIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager transactionManager;
 @Test void createsAllFoundationTablesWithNumericMoney(){
  assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public' and table_name like 'platform_%'",String.class)).containsExactlyInAnyOrder("platform_accounts","platform_campaigns","platform_ad_sets","platform_ads","platform_operations","platform_operation_attempts","platform_metric_snapshots");
  assertThat(jdbc.queryForObject("select numeric_scale from information_schema.columns where table_name='platform_ad_sets' and column_name='budget_amount'",Integer.class)).isEqualTo(6);
  assertThat(jdbc.queryForList("select numeric_scale from information_schema.columns where table_name='platform_metric_snapshots' and column_name in ('spend','revenue') order by column_name",Integer.class)).containsExactly(6,6);
 }
 @Test void rejectsUnsupportedAccountsNonPristineEntitiesAndMalformedDurableCommands(){
  assertThatThrownBy(()->jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'META','PRODUCTION','forbidden',?,'TWD','Asia/Taipei')",UUID.randomUUID(),"d".repeat(64))).isInstanceOf(RuntimeException.class);
  UUID account=account(), plan=UUID.randomUUID(), campaign=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Strict')",plan);
  assertThatThrownBy(()->jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,desired_state,account_timezone) values (?,?,?,'OUTCOME_SALES','ACTIVE','Asia/Taipei')",campaign,plan,account)).isInstanceOf(RuntimeException.class);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  assertThatThrownBy(()->jdbc.update("update platform_campaigns set version=version+1,updated_at=current_timestamp where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_campaigns set external_id='direct-sql-id',version=version+1,updated_at=current_timestamp where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_campaigns set desired_state='ACTIVE',version=version+1,updated_at=current_timestamp where platform_campaign_uuid=?",campaign)).isInstanceOf(RuntimeException.class);
  String base="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
  String malformed=base.substring(0,base.length()-1)+",\"secretToken\":\"sentinel\"}";
  assertThatThrownBy(()->insertOperation(account,campaign,malformed)).isInstanceOf(RuntimeException.class);
  String wrongType=base.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\"");
  assertThatThrownBy(()->insertOperation(account,campaign,wrongType)).isInstanceOf(RuntimeException.class);
  UUID operation=insertOperation(account,campaign,base);
  assertThatThrownBy(()->jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) values (?,?, 'SUBMIT',1,'STARTED',current_timestamp,0)",UUID.randomUUID(),operation)).isInstanceOf(RuntimeException.class);
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
 @Test void exactOutcomeEvidenceMatrixAndRetryDueTimeAreDatabaseEnforced(){
  String submitRetry="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_RETRYABLE\",\"retryAfterSeconds\":60}";
  String submitUnknown="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}";
  String reconcileUnknown="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"RECONCILE\",\"resultKind\":\"UNKNOWN_OUTCOME\"}";
  assertThat(jdbc.queryForObject("select is_valid_platform_attempt_result('SUBMIT','FAILED_RETRYABLE','PLATFORM_RATE_LIMITED',?::jsonb)",Boolean.class,submitRetry)).isTrue();
  assertThat(jdbc.queryForObject("select is_valid_platform_attempt_result('RECONCILE','UNKNOWN_OUTCOME','PLATFORM_RESPONSE_AMBIGUOUS',?::jsonb)",Boolean.class,reconcileUnknown)).isTrue();
  assertThat(jdbc.queryForObject("select is_valid_platform_attempt_result('SUBMIT','FAILED_TERMINAL','PLATFORM_RATE_LIMITED',?::jsonb)",Boolean.class,submitRetry)).isFalse();
  assertThat(jdbc.queryForObject("select is_valid_platform_attempt_result('RECONCILE','UNKNOWN_OUTCOME','PLATFORM_RESPONSE_AMBIGUOUS',?::jsonb)",Boolean.class,submitUnknown)).isFalse();
  Object[][] valid={
    {"SUBMIT","SUCCEEDED",null,evidence("SUBMIT","SUCCEEDED",null)},
    {"SUBMIT","FAILED_RETRYABLE","PLATFORM_RATE_LIMITED",evidence("SUBMIT","FAILED_RETRYABLE",60)},
    {"SUBMIT","FAILED_RETRYABLE","PLATFORM_TEMPORARILY_UNAVAILABLE",evidence("SUBMIT","FAILED_RETRYABLE",30)},
    {"SUBMIT","FAILED_TERMINAL","PLATFORM_VALIDATION_FAILED",evidence("SUBMIT","FAILED_TERMINAL",null)},
    {"SUBMIT","FAILED_TERMINAL","PLATFORM_PERMISSION_DENIED",evidence("SUBMIT","FAILED_TERMINAL",null)},
    {"SUBMIT","FAILED_TERMINAL","PLATFORM_MAX_ATTEMPTS_EXCEEDED",evidence("SUBMIT","FAILED_TERMINAL",null)},
    {"SUBMIT","UNKNOWN_OUTCOME","PLATFORM_RESPONSE_AMBIGUOUS",evidence("SUBMIT","UNKNOWN_OUTCOME",null)},
    {"RECONCILE","SUCCEEDED",null,evidence("RECONCILE","FOUND",null)},
    {"RECONCILE","FAILED_TERMINAL","PLATFORM_RECONCILIATION_TERMINAL",evidence("RECONCILE","FAILED_TERMINAL",null)},
    {"RECONCILE","NOT_FOUND","PLATFORM_RECONCILIATION_NOT_FOUND",evidence("RECONCILE","NOT_FOUND",null)},
    {"RECONCILE","UNKNOWN_OUTCOME","PLATFORM_RECONCILIATION_INCONCLUSIVE",evidence("RECONCILE","STILL_UNKNOWN",null)},
    {"RECONCILE","UNKNOWN_OUTCOME","PLATFORM_RESPONSE_AMBIGUOUS",evidence("RECONCILE","UNKNOWN_OUTCOME",null)}
  };
  for(Object[] row:valid) assertThat(validAttemptResult((String)row[0],(String)row[1],(String)row[2],(String)row[3])).as(java.util.Arrays.toString(row)).isTrue();
  for(Object[] row:valid){
   String wrongKind=row[0].equals("SUBMIT")?"RECONCILE":"SUBMIT";
   assertThat(validAttemptResult(wrongKind,(String)row[1],(String)row[2],(String)row[3])).as("wrong attempt kind "+java.util.Arrays.toString(row)).isFalse();
  }

  Instant due=Instant.parse("2026-08-17T03:00:00Z");
  UUID early=failedRetryableOperation(due,submitRetry);
  assertThatThrownBy(()->jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=2,claimed_at=?,normalized_error_code=null,safe_provider_trace_id=null,outcome_evidence=null,next_attempt_at=null,updated_at=?,version=3 where operation_uuid=?",Timestamp.from(due.minusNanos(1)),Timestamp.from(due.minusNanos(1)),early)).isInstanceOf(RuntimeException.class);
  UUID exact=failedRetryableOperation(due,submitRetry);
  claimRetryDirect(exact,due);
  UUID late=failedRetryableOperation(due,submitRetry);
  claimRetryDirect(late,due.plusSeconds(1));
  assertThat(jdbc.queryForObject("select count(*) from platform_operation_attempts where operation_uuid in (?,?) and attempt_number=2 and status='STARTED'",Integer.class,exact,late)).isEqualTo(2);
 }
 private UUID account(){UUID id=UUID.randomUUID();String fingerprint=id.toString().replace("-","")+"c".repeat(32);jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",id,"acct-"+id,fingerprint);return id;}
 private boolean validAttemptResult(String kind,String status,String code,String evidence){return Boolean.TRUE.equals(jdbc.queryForObject("select is_valid_platform_attempt_result(?,?,?,?::jsonb)",Boolean.class,kind,status,code,evidence));}
 private String evidence(String kind,String result,Integer retry){return "{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\""+kind+"\",\"resultKind\":\""+result+"\""+(retry==null?"":",\"retryAfterSeconds\":"+retry)+"}";}
 private UUID insertOperation(UUID account,UUID campaign,String payload){UUID operation=UUID.randomUUID();jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id,max_attempts) values (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?, ?::jsonb,?,'LOCAL_ADMIN','strict-tester',?,3)",operation,account,campaign,UUID.randomUUID(),UUID.randomUUID().toString().replace("-","").repeat(2),payload,UUID.randomUUID().toString().replace("-","").repeat(2),"strict-"+operation);return operation;}
 private UUID failedRetryableOperation(Instant due,String evidence){
  UUID account=account(),plan=UUID.randomUUID(),campaign=UUID.randomUUID();
  jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Due')",plan);
  jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
  String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
  UUID operation=insertOperation(account,campaign,payload),attempt=UUID.randomUUID(); Instant started=due.minusSeconds(120);
  new TransactionTemplate(transactionManager).executeWithoutResult(s->{jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=?,updated_at=?,version=1 where operation_uuid=?",Timestamp.from(started),Timestamp.from(started),operation);jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) values (?,?,'SUBMIT',1,'STARTED',?,0)",attempt,operation,Timestamp.from(started));});
  new TransactionTemplate(transactionManager).executeWithoutResult(s->{jdbc.update("update platform_operation_attempts set status='FAILED_RETRYABLE',normalized_error_code='PLATFORM_RATE_LIMITED',evidence=?::jsonb,completed_at=?,version=1 where operation_attempt_uuid=?",evidence,Timestamp.from(started.plusSeconds(1)),attempt);jdbc.update("update platform_operations set status='FAILED_RETRYABLE',normalized_error_code='PLATFORM_RATE_LIMITED',outcome_evidence=?::jsonb,next_attempt_at=?,updated_at=?,version=2 where operation_uuid=?",evidence,Timestamp.from(due),Timestamp.from(started.plusSeconds(1)),operation);});
  return operation;
 }
 private void claimRetryDirect(UUID operation,Instant claimed){new TransactionTemplate(transactionManager).executeWithoutResult(s->{jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=2,claimed_at=?,normalized_error_code=null,safe_provider_trace_id=null,outcome_evidence=null,next_attempt_at=null,updated_at=?,version=3 where operation_uuid=?",Timestamp.from(claimed),Timestamp.from(claimed),operation);jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) values (?,?,'SUBMIT',2,'STARTED',?,0)",UUID.randomUUID(),operation,Timestamp.from(claimed));});}
}
