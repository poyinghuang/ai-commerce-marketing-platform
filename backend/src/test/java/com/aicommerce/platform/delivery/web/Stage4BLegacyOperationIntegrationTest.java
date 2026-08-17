package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves physical pre-V13 operations remain readable but cannot cross the Stage 4B execution boundary. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(Stage4BLegacyOperationIntegrationTest.LegacyFixtureMigration.class)
class Stage4BLegacyOperationIntegrationTest {
    private static final UUID ACCOUNT=UUID.fromString("00000000-0000-4000-8000-00000000005b");
    private static final UUID PLAN=UUID.fromString("00000000-0000-4000-8000-000000000451");
    private static final UUID CAMPAIGN=UUID.fromString("00000000-0000-4000-8000-000000000452");
    private static final UUID AD_SET=UUID.fromString("00000000-0000-4000-8000-000000000453");
    private static final UUID OPERATION=UUID.fromString("00000000-0000-4000-8000-000000000454");
    private static final UUID ATTEMPT=UUID.fromString("00000000-0000-4000-8000-000000000455");
    private static final UUID METRIC=UUID.fromString("00000000-0000-4000-8000-000000000456");
    private static final List<UUID> STATE_OPERATIONS=List.of(
        UUID.fromString("00000000-0000-4000-8000-000000000460"),UUID.fromString("00000000-0000-4000-8000-000000000461"),OPERATION,
        UUID.fromString("00000000-0000-4000-8000-000000000462"),UUID.fromString("00000000-0000-4000-8000-000000000463"),
        UUID.fromString("00000000-0000-4000-8000-000000000464"),UUID.fromString("00000000-0000-4000-8000-000000000465"),UUID.fromString("00000000-0000-4000-8000-000000000466"));
    private static final List<String> EXPECTED_STATES=List.of("CREATED","SUBMITTING","FAILED_RETRYABLE","UNKNOWN_OUTCOME","RECONCILING","SUCCEEDED","FAILED_TERMINAL","FAILED_TERMINAL");
    private static final Map<String,Object> V12_OPERATION=new LinkedHashMap<>();
    private static final Map<String,Object> V12_ATTEMPT=new LinkedHashMap<>();
    private static final Map<String,Object> V12_METRIC=new LinkedHashMap<>();
    private static final Map<String,Object> V12_AD_SET=new LinkedHashMap<>();

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeterministicFakePlatformAdapter fake;

    @Test
    void populatedV12RowsAreBytePreservedReadableAndLegacyRetryReconcileAreInert() throws Exception {
        assertThat(row("platform_operations","operation_uuid",OPERATION)).isEqualTo(V12_OPERATION);
        assertThat(row("platform_operation_attempts","operation_attempt_uuid",ATTEMPT)).isEqualTo(V12_ATTEMPT);
        assertThat(row("platform_metric_snapshots","metric_snapshot_uuid",METRIC)).isEqualTo(V12_METRIC);
        assertThat(row("platform_ad_sets","platform_ad_set_uuid",AD_SET)).isEqualTo(V12_AD_SET);
        assertThat(jdbc.queryForObject("select count(*) from platform_operation_batches where operation_uuid=?",Integer.class,OPERATION)).isZero();

        assertThat(STATE_OPERATIONS.stream().map(operation->jdbc.queryForObject("select status from platform_operations where operation_uuid=?",String.class,operation)).toList()).containsExactlyElementsOf(EXPECTED_STATES);

        Snapshot before=snapshot(); int calls=fake.invocationCount();
        for(int index=0;index<STATE_OPERATIONS.size();index++){
            UUID operation=STATE_OPERATIONS.get(index);long version=jdbc.queryForObject("select version from platform_operations where operation_uuid=?",Long.class,operation);
            mvc.perform(get("/api/platform-operations/"+operation)).andExpect(status().isOk()).andExpect(jsonPath("$.operationUuid").value(operation.toString())).andExpect(jsonPath("$.status").value(EXPECTED_STATES.get(index)));
            mvc.perform(post("/api/platform-operations/"+operation+"/retry").header("If-Match","W/\""+version+"\"")) .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
            mvc.perform(post("/api/platform-operations/"+operation+"/reconcile").header("If-Match","W/\""+version+"\"")) .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
        }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    private Map<String,Object> row(String table,String key,UUID id){return jdbc.queryForMap("select * from "+table+" where "+key+"=?",id);}
    private Snapshot snapshot(){return new Snapshot(
        jdbc.queryForObject("select count(*) from platform_operations",Integer.class),
        jdbc.queryForObject("select count(*) from platform_operation_attempts",Integer.class),
        jdbc.queryForObject("select count(*) from platform_operation_batches",Integer.class),
        jdbc.queryForObject("select count(*) from platform_budget_reservations",Integer.class),
        jdbc.queryForObject("select count(*) from audit_logs",Integer.class),rows("platform_operations","operation_uuid"),rows("platform_operation_attempts","operation_attempt_uuid"),rows("platform_campaigns","platform_campaign_uuid"),rows("platform_ad_sets","platform_ad_set_uuid"),rows("platform_metric_snapshots","metric_snapshot_uuid"));}
    private String rows(String table,String order){return jdbc.queryForObject("select coalesce(jsonb_agg(to_jsonb(t) order by "+order+"),'[]')::text from "+table+" t",String.class);}
    private record Snapshot(int operations,int attempts,int batches,int reservations,int audits,String operationRows,String attemptRows,String campaignRows,String adSetRows,String metricRows){}

    @TestConfiguration(proxyBeanMethods=false)
    static class LegacyFixtureMigration {
        @Bean
        FlywayMigrationStrategy legacyV12ThenV13(DataSource dataSource){
            return latest->{
                Flyway v12=Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("12")).load();
                v12.migrate();
                JdbcTemplate jdbc=new JdbcTemplate(dataSource);
                jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST','stage4b-test',?,'TWD','Asia/Taipei')",ACCOUNT,"9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2");
                jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) values (?,'Legacy V12',current_date+10,current_date+20,'OUTCOME_SALES','META',100,300,'TWD')",PLAN);
                jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,schedule_start,schedule_end,account_timezone) values (?,?,?,'OUTCOME_SALES',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei')",CAMPAIGN,PLAN,ACCOUNT);
                jdbc.update("insert into platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,schedule_start,schedule_end,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key) values (?,?,?,'DAILY',25,'TWD',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1')",AD_SET,CAMPAIGN,ACCOUNT);
                jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_ad_set_uuid,window_start,window_end,timezone,currency,impressions,clicks,spend,revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?, 'AD_SET',?,current_timestamp-interval '1 day',current_timestamp,'Asia/Taipei','TWD',100,7,12.345678,1,current_timestamp,'FRESH',?)",METRIC,ACCOUNT,AD_SET,"a".repeat(64));
                String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+AD_SET+"\",\"platformAdSetUuid\":\""+AD_SET+"\",\"expectedEntityVersion\":0,\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":25,\"newBudgetAmount\":30}";
                UUID request=UUID.fromString("00000000-0000-4000-8000-000000000457");
                jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_set_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) values (?,?, 'UPDATE_BUDGET','AD_SET',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','legacy-v12')",OPERATION,ACCOUNT,AD_SET,request,"b".repeat(64),payload,"c".repeat(64));
                TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                tx.executeWithoutResult(s->{
                    jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=1,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=1 where operation_uuid=?",OPERATION);
                    jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) select ?,operation_uuid,'SUBMIT',1,'STARTED',claimed_at,0 from platform_operations where operation_uuid=?",ATTEMPT,OPERATION);
                });
                UUID created=STATE_OPERATIONS.get(0),submitting=STATE_OPERATIONS.get(1),unknown=STATE_OPERATIONS.get(3),reconciling=STATE_OPERATIONS.get(4),succeeded=STATE_OPERATIONS.get(5),submitTerminal=STATE_OPERATIONS.get(6),reconcileTerminal=STATE_OPERATIONS.get(7);
                for(UUID operation:List.of(created,submitting,unknown,reconciling,succeeded,submitTerminal,reconcileTerminal))insertOperation(jdbc,operation);
                claimSubmit(jdbc,tx,submitting);
                claimSubmit(jdbc,tx,unknown);finalizeSubmit(jdbc,tx,unknown,"UNKNOWN_OUTCOME","PLATFORM_RESPONSE_AMBIGUOUS","{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}");
                claimSubmit(jdbc,tx,reconciling);finalizeSubmit(jdbc,tx,reconciling,"UNKNOWN_OUTCOME","PLATFORM_RESPONSE_AMBIGUOUS","{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}");claimReconcile(jdbc,tx,reconciling);
                claimSubmit(jdbc,tx,submitTerminal);finalizeSubmit(jdbc,tx,submitTerminal,"FAILED_TERMINAL","PLATFORM_VALIDATION_FAILED","{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_TERMINAL\"}");
                claimSubmit(jdbc,tx,reconcileTerminal);finalizeSubmit(jdbc,tx,reconcileTerminal,"UNKNOWN_OUTCOME","PLATFORM_RESPONSE_AMBIGUOUS","{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"UNKNOWN_OUTCOME\"}");claimReconcile(jdbc,tx,reconcileTerminal);finalizeReconcileTerminal(jdbc,tx,reconcileTerminal);
                claimSubmit(jdbc,tx,succeeded);String success="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"SUCCEEDED\"}";tx.executeWithoutResult(s->{jdbc.update("update platform_operation_attempts set status='SUCCEEDED',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_uuid=? and attempt_kind='SUBMIT'",success,succeeded);jdbc.update("update platform_operations set status='SUCCEEDED',outcome_evidence=?::jsonb,claimed_at=null,completed_at=statement_timestamp(),updated_at=statement_timestamp(),version=2 where operation_uuid=?",success,succeeded);jdbc.update("update platform_ad_sets set budget_amount=30,last_budget_operation_uuid=?,updated_at=statement_timestamp(),version=1 where platform_ad_set_uuid=?",succeeded,AD_SET);});
                String evidence="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"SUBMIT\",\"resultKind\":\"FAILED_RETRYABLE\",\"retryAfterSeconds\":60}";
                tx.executeWithoutResult(s->{
                    jdbc.update("update platform_operation_attempts set status='FAILED_RETRYABLE',normalized_error_code='PLATFORM_RATE_LIMITED',safe_provider_trace_id='legacy-trace',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_attempt_uuid=?",evidence,ATTEMPT);
                    jdbc.update("update platform_operations set status='FAILED_RETRYABLE',normalized_error_code='PLATFORM_RATE_LIMITED',safe_provider_trace_id='legacy-trace',outcome_evidence=?::jsonb,next_attempt_at=(select completed_at+interval '60 seconds' from platform_operation_attempts where operation_attempt_uuid=?),claimed_at=null,updated_at=statement_timestamp(),version=2 where operation_uuid=?",evidence,ATTEMPT,OPERATION);
                });
                V12_OPERATION.putAll(jdbc.queryForMap("select * from platform_operations where operation_uuid=?",OPERATION));
                V12_ATTEMPT.putAll(jdbc.queryForMap("select * from platform_operation_attempts where operation_attempt_uuid=?",ATTEMPT));
                V12_METRIC.putAll(jdbc.queryForMap("select * from platform_metric_snapshots where metric_snapshot_uuid=?",METRIC));
                V12_AD_SET.putAll(jdbc.queryForMap("select * from platform_ad_sets where platform_ad_set_uuid=?",AD_SET));
                latest.migrate();
            };
        }
        private static void insertOperation(JdbcTemplate jdbc,UUID operation){UUID request=UUID.nameUUIDFromBytes(operation.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+AD_SET+"\",\"platformAdSetUuid\":\""+AD_SET+"\",\"expectedEntityVersion\":0,\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":25,\"newBudgetAmount\":30}";jdbc.update("insert into platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_set_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) values (?,?, 'UPDATE_BUDGET','AD_SET',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin',?)",operation,ACCOUNT,AD_SET,request,hex(operation),payload,hex(request),"legacy-"+operation.toString().substring(30));}
        private static void claimSubmit(JdbcTemplate jdbc,TransactionTemplate tx,UUID operation){tx.executeWithoutResult(s->{jdbc.update("update platform_operations set status='SUBMITTING',attempt_count=attempt_count+1,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 where operation_uuid=?",operation);jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) select ?,operation_uuid,'SUBMIT',attempt_count,'STARTED',claimed_at,0 from platform_operations where operation_uuid=?",UUID.randomUUID(),operation);});}
        private static void finalizeSubmit(JdbcTemplate jdbc,TransactionTemplate tx,UUID operation,String status,String code,String evidence){tx.executeWithoutResult(s->{jdbc.update("update platform_operation_attempts set status=?,normalized_error_code=?,safe_provider_trace_id='legacy-trace',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_uuid=? and attempt_kind='SUBMIT'",status,code,evidence,operation);jdbc.update("update platform_operations set status=?,normalized_error_code=?,safe_provider_trace_id='legacy-trace',outcome_evidence=?::jsonb,claimed_at=null,completed_at=case when ?='FAILED_TERMINAL' then statement_timestamp() else null end,updated_at=statement_timestamp(),version=version+1 where operation_uuid=?",status,code,evidence,status,operation);});}
        private static void claimReconcile(JdbcTemplate jdbc,TransactionTemplate tx,UUID operation){tx.executeWithoutResult(s->{jdbc.update("update platform_operations set status='RECONCILING',reconciliation_count=reconciliation_count+1,normalized_error_code=null,safe_provider_trace_id=null,outcome_evidence=null,claimed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 where operation_uuid=?",operation);jdbc.update("insert into platform_operation_attempts(operation_attempt_uuid,operation_uuid,attempt_kind,attempt_number,status,started_at,version) select ?,operation_uuid,'RECONCILE',reconciliation_count,'STARTED',claimed_at,0 from platform_operations where operation_uuid=?",UUID.randomUUID(),operation);});}
        private static void finalizeReconcileTerminal(JdbcTemplate jdbc,TransactionTemplate tx,UUID operation){String evidence="{\"schemaVersion\":1,\"providerKey\":\"FAKE\",\"attemptKind\":\"RECONCILE\",\"resultKind\":\"FAILED_TERMINAL\"}";tx.executeWithoutResult(s->{jdbc.update("update platform_operation_attempts set status='FAILED_TERMINAL',normalized_error_code='PLATFORM_RECONCILIATION_TERMINAL',safe_provider_trace_id='legacy-trace',evidence=?::jsonb,completed_at=statement_timestamp(),version=1 where operation_uuid=? and attempt_kind='RECONCILE'",evidence,operation);jdbc.update("update platform_operations set status='FAILED_TERMINAL',normalized_error_code='PLATFORM_RECONCILIATION_TERMINAL',safe_provider_trace_id='legacy-trace',outcome_evidence=?::jsonb,claimed_at=null,completed_at=statement_timestamp(),updated_at=statement_timestamp(),version=version+1 where operation_uuid=?",evidence,operation);});}
        private static String hex(UUID id){return id.toString().replace("-","").repeat(2);}
    }
}
