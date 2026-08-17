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

    @Test void directSqlIdentityDateCurrencyKindDeltaAggregateAndForgedClockMatrixRollsBackExactly() {
        UUID plan=plan();var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"matrix-campaign").operation();var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("25"),0,0,"matrix-adset").operation();
        UUID batch=jdbc.queryForObject("SELECT operation_batch_uuid FROM platform_operation_batches WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());UUID reservation=jdbc.queryForObject("SELECT budget_reservation_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());UUID day=jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        Map<String,Object> batchBefore=row("platform_operation_batches","operation_batch_uuid",batch),reservationBefore=row("platform_budget_reservations","budget_reservation_uuid",reservation),dayBefore=row("platform_account_budget_days","account_budget_day_uuid",day);int operations=count("platform_operations"),batches=count("platform_operation_batches"),reservations=count("platform_budget_reservations"),days=count("platform_account_budget_days");
        List<SqlCase> cases=List.of(
            new SqlCase("batch identity","UPDATE platform_operation_batches SET platform_account_uuid=? WHERE operation_batch_uuid=?",UUID.randomUUID(),batch),
            new SqlCase("batch date","UPDATE platform_operation_batches SET business_date=business_date+1 WHERE operation_batch_uuid=?",batch),
            new SqlCase("batch currency","UPDATE platform_operation_batches SET currency='USD' WHERE operation_batch_uuid=?",batch),
            new SqlCase("batch delta","UPDATE platform_operation_batches SET reserved_amount=reserved_amount+1 WHERE operation_batch_uuid=?",batch),
            new SqlCase("batch forged clock","UPDATE platform_operation_batches SET created_at=created_at+interval '1 day' WHERE operation_batch_uuid=?",batch),
            new SqlCase("reservation identity","UPDATE platform_budget_reservations SET operation_uuid=? WHERE budget_reservation_uuid=?",UUID.randomUUID(),reservation),
            new SqlCase("reservation date","UPDATE platform_budget_reservations SET business_date=business_date+1 WHERE budget_reservation_uuid=?",reservation),
            new SqlCase("reservation currency","UPDATE platform_budget_reservations SET currency='USD' WHERE budget_reservation_uuid=?",reservation),
            new SqlCase("reservation kind","UPDATE platform_budget_reservations SET reservation_kind='DECREASE_NO_RELEASE' WHERE budget_reservation_uuid=?",reservation),
            new SqlCase("reservation zero delta","UPDATE platform_budget_reservations SET reserved_amount=0 WHERE budget_reservation_uuid=?",reservation),
            new SqlCase("reservation forged clock","UPDATE platform_budget_reservations SET created_at=created_at+interval '1 day' WHERE budget_reservation_uuid=?",reservation),
            new SqlCase("day identity","UPDATE platform_account_budget_days SET platform_account_uuid=? WHERE account_budget_day_uuid=?",UUID.randomUUID(),day),
            new SqlCase("day date","UPDATE platform_account_budget_days SET business_date=business_date+1 WHERE account_budget_day_uuid=?",day),
            new SqlCase("day currency","UPDATE platform_account_budget_days SET currency='USD' WHERE account_budget_day_uuid=?",day),
            new SqlCase("day aggregate","UPDATE platform_account_budget_days SET reserved_amount=reserved_amount+1 WHERE account_budget_day_uuid=?",day),
            new SqlCase("day forged clock","UPDATE platform_account_budget_days SET updated_at=updated_at+interval '1 day' WHERE account_budget_day_uuid=?",day));
        cases.forEach(test->assertSqlState23514(()->jdbc.update(test.sql(),test.arguments())));
        assertThat(row("platform_operation_batches","operation_batch_uuid",batch)).isEqualTo(batchBefore);assertThat(row("platform_budget_reservations","budget_reservation_uuid",reservation)).isEqualTo(reservationBefore);assertThat(row("platform_account_budget_days","account_budget_day_uuid",day)).isEqualTo(dayBefore);
        assertThat(count("platform_operations")).isEqualTo(operations);assertThat(count("platform_operation_batches")).isEqualTo(batches);assertThat(count("platform_budget_reservations")).isEqualTo(reservations);assertThat(count("platform_account_budget_days")).isEqualTo(days);
    }

    private UUID plan(){UUID id=UUID.randomUUID();jdbc.update("""
      INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
      VALUES (?,'Stage 4B ledger',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
      """,id,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return id;}
    private Map<String,Object> row(String table,String key,UUID id){return jdbc.queryForMap("SELECT * FROM "+table+" WHERE "+key+"=?",id);}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private record SqlCase(String name,String sql,Object...arguments){}
    private static void assertSqlState23514(org.assertj.core.api.ThrowableAssert.ThrowingCallable call){
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class).satisfies(failure->{
            Throwable current=failure;while(current.getCause()!=null)current=current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo("23514");
        });
    }
}
