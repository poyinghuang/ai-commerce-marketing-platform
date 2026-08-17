package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.aicommerce.platform.delivery.application.Stage4BException;
import com.aicommerce.platform.delivery.application.Stage4BTransactions;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Stage4BLedgerConcurrencyIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired Stage4BTransactions transactions;
    @Autowired JdbcTemplate jdbc;

    @Test void barrierControlledFirstUseSerializesBelowAtAndAboveCeilingWithLoserRollback() throws Exception {
        UUID plan=UUID.randomUUID();jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
          VALUES (?,'Concurrency',?,?, 'OUTCOME_SALES','META',1000,1000,'TWD')
          """,plan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));
        UUID campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"stage4b-concurrency").operation().getEntityUuid();

        var below=concurrent(campaign,"200","300");
        assertThat(below.successes()).isEqualTo(2);assertThat(below.capFailures()).isZero();
        assertThat(reserved()).isEqualByComparingTo("500");

        Snapshot beforeAbove=snapshot();var above=concurrent(campaign,"300","300");
        assertThat(above.successes()).isEqualTo(1);assertThat(above.capFailures()).isEqualTo(1);
        assertThat(reserved()).isEqualByComparingTo("800");
        Snapshot afterAbove=snapshot();
        assertThat(afterAbove.operations()-beforeAbove.operations()).isEqualTo(1);
        assertThat(afterAbove.batches()-beforeAbove.batches()).isEqualTo(1);
        assertThat(afterAbove.reservations()-beforeAbove.reservations()).isEqualTo(1);

        transactions.confirmAdSet(campaign,UUID.randomUUID(),PlatformBudgetType.LIFETIME,new BigDecimal("200"),0,0,"stage4b-at-cap");
        assertThat(reserved()).isEqualByComparingTo("1000");
        Snapshot atCap=snapshot();
        try{transactions.confirmAdSet(campaign,UUID.randomUUID(),PlatformBudgetType.LIFETIME,BigDecimal.ONE,0,0,"stage4b-over-cap");}
        catch(Stage4BException failure){assertThat(failure.code()).isEqualTo("PLATFORM_BUDGET_CAP_EXCEEDED");}
        assertThat(snapshot()).isEqualTo(atCap);
    }

    private Outcome concurrent(UUID campaign,String first,String second) throws Exception {
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            Future<String> one=executor.submit(()->attempt(campaign,new BigDecimal(first),ready,start));
            Future<String> two=executor.submit(()->attempt(campaign,new BigDecimal(second),ready,start));
            ready.await();start.countDown();String a=one.get(),b=two.get();
            return new Outcome(("SUCCESS".equals(a)?1:0)+("SUCCESS".equals(b)?1:0),("PLATFORM_BUDGET_CAP_EXCEEDED".equals(a)?1:0)+("PLATFORM_BUDGET_CAP_EXCEEDED".equals(b)?1:0));
        }
    }
    private String attempt(UUID campaign,BigDecimal amount,CountDownLatch ready,CountDownLatch start){ready.countDown();try{start.await();transactions.confirmAdSet(campaign,UUID.randomUUID(),PlatformBudgetType.LIFETIME,amount,0,0,"stage4b-barrier");return "SUCCESS";}catch(Stage4BException failure){return failure.code();}catch(Exception failure){throw new RuntimeException(failure);}}
    private BigDecimal reserved(){return jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days",BigDecimal.class);}
    private Snapshot snapshot(){return new Snapshot(count("platform_operations"),count("platform_operation_batches"),count("platform_budget_reservations"),count("audit_logs"));}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private record Outcome(int successes,int capFailures){}
    private record Snapshot(int operations,int batches,int reservations,int audits){}
}
