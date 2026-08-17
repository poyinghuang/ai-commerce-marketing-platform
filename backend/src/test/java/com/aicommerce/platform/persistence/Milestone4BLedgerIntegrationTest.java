package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import com.aicommerce.platform.delivery.application.Stage4BTransactions;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone4BLedgerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc;
    @Autowired Stage4BTransactions transactions;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void v13TablesHibernateMappingsAndTaipeiBoundaryAreAvailable() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('platform_operation_batches','platform_budget_reservations','platform_account_budget_days')",String.class))
                .containsExactlyInAnyOrder("platform_operation_batches","platform_budget_reservations","platform_account_budget_days");
        assertThat(jdbc.queryForObject("SELECT platform_taipei_business_date('2026-01-01T15:59:59Z')",LocalDate.class)).isEqualTo(LocalDate.of(2026,1,1));
        assertThat(jdbc.queryForObject("SELECT platform_taipei_business_date('2026-01-01T16:00:00Z')",LocalDate.class)).isEqualTo(LocalDate.of(2026,1,2));
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success AND version='13'",String.class)).isEqualTo("13");
    }

    @Test
    void successfulBudgetAuthorizationIsAnchoredAndAllLedgerRowsRejectMutationAndDelete() {
        BigDecimal dayBefore=jdbc.queryForObject("SELECT COALESCE(sum(reserved_amount),0) FROM platform_account_budget_days",BigDecimal.class);
        UUID plan=plan();
        var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"stage4b-ledger-campaign").operation();
        var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,
                new BigDecimal("50"),0,0,"stage4b-ledger-adset").operation();
        UUID batch=jdbc.queryForObject("SELECT operation_batch_uuid FROM platform_operation_batches WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID reservation=jdbc.queryForObject("SELECT budget_reservation_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID day=jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_operation_batches WHERE operation_batch_uuid=?",BigDecimal.class,batch)).isEqualByComparingTo("50");
        BigDecimal expectedDay=dayBefore.add(new BigDecimal("50"));assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo(expectedDay);

        assertSqlState23514(() -> jdbc.update("UPDATE platform_operation_batches SET reserved_amount=49 WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_operation_batches WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_budget_reservations SET reserved_amount=49 WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_budget_reservations WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_account_budget_days SET reserved_amount=51 WHERE account_budget_day_uuid=?",day));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_account_budget_days WHERE account_budget_day_uuid=?",day));
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo(expectedDay);
    }

    @Test void arbitraryAccountPostV13Stage4BOperationCannotCommitWithoutBatch() {
        UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),operation=UUID.randomUUID(),request=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"universal-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name) VALUES (?,'Universal')",plan);
        jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
        String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
        assertSqlState23514(()->new TransactionTemplate(transactionManager).executeWithoutResult(s->jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','universal')",operation,account,campaign,request,"a".repeat(64),payload,"b".repeat(64))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operations WHERE operation_uuid=?",Integer.class,operation)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE operation_uuid=?",Integer.class,operation)).isZero();
    }

    @Test void malformedBatchFirstInsertAndDeferredCommitMatrixRollsBackEntireGraph() {
        Graph graph=graph();String pristine=snapshot();
        assertState("23503",()->tx(()->insertBatch(graph,UUID.randomUUID(),BigDecimal.ZERO),true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID();insertOperation(graph,operation,UUID.randomUUID(),"25","30");insertBatch(graph,operation,new BigDecimal("5"));},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"));insertReservation(graph,batch,UUID.randomUUID(),graph.day,"INCREASE","25","30","5");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->insertReservation(graph,UUID.randomUUID(),UUID.randomUUID(),graph.day,"INCREASE","25","30","5"),false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID();insertBatch(graph,operation,new BigDecimal("5"));insertOperation(graph,operation,UUID.randomUUID(),"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23505",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("10"),request);insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");},false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23503",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,UUID.randomUUID(),"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID();insertBatch(graph,operation,BigDecimal.ZERO,request);insertUnapprovedOperation(graph,operation,request);},false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->insertBatch(graph,graph.adSetCreateOperation,BigDecimal.ZERO),false));assertThat(snapshot()).isEqualTo(pristine);
    }

    @Test void forgedAnchorsAreDatabaseOwnedAndZeroReleaseIsValidWithoutDayMutation() {
        Graph graph=graph();String pristine=snapshot();
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,BigDecimal.ZERO,request);insertReservation(graph,batch,operation,graph.day,"DECREASE_NO_RELEASE","25","20","0");insertOperation(graph,operation,request,"25","20");jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");Map<String,Object> anchored=row("platform_operation_batches","operation_batch_uuid",batch);assertThat(anchored.get("currency")).isEqualTo("TWD");assertThat(anchored.get("version")).isEqualTo(0L);assertThat(jdbc.queryForObject("SELECT business_date FROM platform_operation_batches WHERE operation_batch_uuid=?",LocalDate.class,batch)).isEqualTo(LocalDate.now());assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations WHERE operation_uuid=? AND created_at=(SELECT created_at FROM platform_operation_batches WHERE operation_uuid=?)",Integer.class,operation,operation)).isEqualTo(1);status.setRollbackOnly();});
        assertThat(snapshot()).isEqualTo(pristine);
    }

    private UUID plan(){UUID id=UUID.randomUUID();jdbc.update("""
      INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
      VALUES (?,'Stage 4B ledger',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
      """,id,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return id;}
    private Graph graph(){UUID p=plan();var campaign=transactions.confirmCampaign(UUID.randomUUID(),p,0,"direct-sql-campaign").operation();var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("25"),0,0,"direct-sql-adset").operation();return new Graph(jdbc.queryForObject("SELECT platform_account_uuid FROM platform_ad_sets WHERE platform_ad_set_uuid=?",UUID.class,adSet.getEntityUuid()),adSet.getEntityUuid(),jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid()),adSet.getOperationUuid());}
    private UUID insertBatch(Graph graph,UUID operation,BigDecimal amount){return insertBatch(graph,operation,amount,UUID.randomUUID());}
    private UUID insertBatch(Graph graph,UUID operation,BigDecimal amount,UUID request){UUID batch=UUID.randomUUID();jdbc.update("INSERT INTO platform_operation_batches(operation_batch_uuid,operation_uuid,platform_account_uuid,client_request_uuid,requested_actor_type,requested_actor_id,expected_entity_version,currency,business_date,reserved_amount,created_at,version) VALUES (?,?,?,?,'LOCAL_ADMIN','local-admin',0,'USD',DATE '1999-01-01',?,TIMESTAMPTZ '2099-01-01 00:00:00Z',99)",batch,operation,graph.account,request,amount);return batch;}
    private void insertReservation(Graph graph,UUID batch,UUID operation,UUID day,String kind,String previous,String next,String reserved){jdbc.update("INSERT INTO platform_budget_reservations(budget_reservation_uuid,operation_batch_uuid,operation_uuid,platform_account_uuid,account_budget_day_uuid,platform_ad_set_uuid,reservation_kind,previous_budget_amount,new_budget_amount,reserved_amount,currency,business_date,created_at) VALUES (?,?,?,?,?,?,?,?::numeric,?::numeric,?::numeric,'USD',DATE '1999-01-01',TIMESTAMPTZ '2099-01-01 00:00:00Z')",UUID.randomUUID(),batch,operation,graph.account,day,graph.adSet,kind,previous,next,reserved);}
    private void insertOperation(Graph graph,UUID operation,UUID request,String previous,String next){String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+graph.adSet+"\",\"platformAdSetUuid\":\""+graph.adSet+"\",\"expectedEntityVersion\":0,\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":"+previous+",\"newBudgetAmount\":"+next+"}";jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_set_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?,'UPDATE_BUDGET','AD_SET',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','direct-sql')",operation,graph.account,graph.adSet,request,hex(operation),payload,hex(request));}
    private void insertUnapprovedOperation(Graph graph,UUID operation,UUID request){String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+graph.adSet+"\"}";jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_set_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?,'CREATE_AD','AD_SET',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','direct-sql')",operation,graph.account,graph.adSet,request,hex(operation),payload,hex(request));}
    private void tx(Runnable work,boolean forceDeferred){new TransactionTemplate(transactionManager).executeWithoutResult(status->{work.run();if(forceDeferred)jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");});}
    private String snapshot(){return jdbc.queryForObject("SELECT jsonb_build_object('operations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_uuid) FROM platform_operations t),'batches',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_batch_uuid) FROM platform_operation_batches t),'reservations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY budget_reservation_uuid) FROM platform_budget_reservations t),'days',(SELECT jsonb_agg(to_jsonb(t) ORDER BY account_budget_day_uuid) FROM platform_account_budget_days t),'audit',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid) FROM audit_logs t),'changes',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid,change_order) FROM audit_log_changes t))::text",String.class);}
    private static String hex(UUID id){return id.toString().replace("-","").repeat(2);}
    private static void assertState(String state,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){assertThatThrownBy(call).satisfies(failure->{Throwable current=failure;while(current.getCause()!=null)current=current.getCause();assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo(state);});}
    private Map<String,Object> row(String table,String key,UUID id){return jdbc.queryForMap("SELECT * FROM "+table+" WHERE "+key+"=?",id);}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private record SqlCase(String name,String sql,Object...arguments){}
    private record Graph(UUID account,UUID adSet,UUID day,UUID adSetCreateOperation){}
    private static void assertSqlState23514(org.assertj.core.api.ThrowableAssert.ThrowingCallable call){
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class).satisfies(failure->{
            Throwable current=failure;while(current.getCause()!=null)current=current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo("23514");
        });
    }
}
