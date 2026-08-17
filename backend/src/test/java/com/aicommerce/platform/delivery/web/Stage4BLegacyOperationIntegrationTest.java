package com.aicommerce.platform.delivery.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
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

        mvc.perform(get("/api/platform-operations/"+OPERATION))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operationUuid").value(OPERATION.toString()))
            .andExpect(jsonPath("$.status").value("FAILED_RETRYABLE"));

        Snapshot before=snapshot(); int calls=fake.invocationCount();
        mvc.perform(post("/api/platform-operations/"+OPERATION+"/retry").header("If-Match","W/\"2\""))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
        mvc.perform(post("/api/platform-operations/"+OPERATION+"/reconcile").header("If-Match","W/\"2\""))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_LEGACY_OPERATION_INERT"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(fake.invocationCount()).isEqualTo(calls);
    }

    private Map<String,Object> row(String table,String key,UUID id){return jdbc.queryForMap("select * from "+table+" where "+key+"=?",id);}
    private Snapshot snapshot(){return new Snapshot(
        jdbc.queryForObject("select count(*) from platform_operations",Integer.class),
        jdbc.queryForObject("select count(*) from platform_operation_attempts",Integer.class),
        jdbc.queryForObject("select count(*) from platform_operation_batches",Integer.class),
        jdbc.queryForObject("select count(*) from platform_budget_reservations",Integer.class),
        jdbc.queryForObject("select count(*) from audit_logs",Integer.class),
        row("platform_operations","operation_uuid",OPERATION),row("platform_operation_attempts","operation_attempt_uuid",ATTEMPT));}
    private record Snapshot(int operations,int attempts,int batches,int reservations,int audits,Map<String,Object> operation,Map<String,Object> attempt){}

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
    }
}
