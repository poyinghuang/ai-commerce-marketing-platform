package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

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
        UUID plan=plan();
        var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"stage4b-ledger-campaign").operation();
        var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,
                new BigDecimal("50"),0,0,"stage4b-ledger-adset").operation();
        UUID batch=jdbc.queryForObject("SELECT operation_batch_uuid FROM platform_operation_batches WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID reservation=jdbc.queryForObject("SELECT budget_reservation_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID day=jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_operation_batches WHERE operation_batch_uuid=?",BigDecimal.class,batch)).isEqualByComparingTo("50");
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo("50");

        assertSqlState23514(() -> jdbc.update("UPDATE platform_operation_batches SET reserved_amount=49 WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_operation_batches WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_budget_reservations SET reserved_amount=49 WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_budget_reservations WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_account_budget_days SET reserved_amount=51 WHERE account_budget_day_uuid=?",day));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_account_budget_days WHERE account_budget_day_uuid=?",day));
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo("50");
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

    private UUID plan(){UUID id=UUID.randomUUID();jdbc.update("""
      INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
      VALUES (?,'Stage 4B ledger',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
      """,id,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return id;}
    private static void assertSqlState23514(org.assertj.core.api.ThrowableAssert.ThrowingCallable call){
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class).satisfies(failure->{
            Throwable current=failure;while(current.getCause()!=null)current=current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo("23514");
        });
    }
}
