package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.aicommerce.platform.delivery.application.Stage4BException;
import com.aicommerce.platform.delivery.application.Stage4BLedgerCriticalSectionHook;
import com.aicommerce.platform.delivery.application.Stage4BTransactions;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class Stage4BLedgerConcurrencyIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired Stage4BTransactions transactions; @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean Stage4BLedgerCriticalSectionHook hook;

    @BeforeEach void cleanLedger(){reset(hook);jdbc.execute("TRUNCATE platform_budget_reservations, platform_operation_batches, platform_account_budget_days, platform_operation_attempts, platform_operations, platform_ad_sets, platform_campaigns, campaign_plans, audit_log_changes, audit_logs RESTART IDENTITY CASCADE");}

    @Test void freshDayBelowCeilingCommitsBothSeparateParentTransactions() throws Exception {
        ConcurrentResult result=concurrentCreates("200","300",false);
        assertThat(result.first.code).isEqualTo("SUCCESS");assertThat(result.second.code).isEqualTo("SUCCESS");assertDayAndRows("500",2,2);assertThat(result.first.entity).isNotEqualTo(result.second.entity);
    }

    @Test void freshDayExactCeilingCommitsBothAfterIndependentBaseline() throws Exception {
        var results=concurrentCreates(java.util.List.of("250","250","250","250"),-1);
        assertThat(results).extracting(Result::code).containsOnly("SUCCESS");assertDayAndRows("1000",4,4);assertThat(results).extracting(Result::entity).doesNotHaveDuplicates();
    }

    @Test void freshDayAboveCeilingHasDeterministicLoserAndCompleteRollback() throws Exception {
        String before=allRows();var results=concurrentCreates(java.util.List.of("300","300","300","300"),3);Result loser=results.get(3);
        assertThat(results.subList(0,3)).extracting(Result::code).containsOnly("SUCCESS");assertThat(loser.code).isEqualTo("PLATFORM_BUDGET_CAP_EXCEEDED");assertDayAndRows("900",3,3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operations WHERE client_request_uuid=?",Integer.class,loser.request)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE client_request_uuid=?",Integer.class,loser.request)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE request_id='ledger-worker-3'",Integer.class)).isZero();
        assertThat(allRows()).doesNotContain(loser.request.toString());assertThat(before).doesNotContain(loser.request.toString());
    }

    @Test void decreaseFirstDoesNotReleaseDayAndConcurrentIncreaseAdvancesExactlyOnce() throws Exception {
        UUID first=legacyAdSet("50"),second=legacyAdSet("50");assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_account_budget_days",Integer.class)).isZero();CyclicBarrier barrier=new CyclicBarrier(2);CountDownLatch decreaseCommitted=new CountDownLatch(1);
        doAnswer(invocation->{barrier.await();if(Thread.currentThread().getName().equals("increase-second"))decreaseCommitted.await();return null;}).when(hook).beforeAccountDayClaim();
        try(var decreaseExecutor=Executors.newSingleThreadExecutor(r->new Thread(r,"decrease-first"));var increaseExecutor=Executors.newSingleThreadExecutor(r->new Thread(r,"increase-second"))){Future<Result> decrease=decreaseExecutor.submit(()->{Result result=budget(first,"40","decrease-first");decreaseCommitted.countDown();return result;});Future<Result> increase=increaseExecutor.submit(()->budget(second,"100","increase-second"));assertThat(decrease.get().code).isEqualTo("SUCCESS");assertThat(increase.get().code).isEqualTo("SUCCESS");}
        assertThat(dayVersion()).isEqualTo(1);assertDayAndRows("50",2,1);assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations WHERE reservation_kind='DECREASE_NO_RELEASE' AND reserved_amount=0",Integer.class)).isEqualTo(1);
    }

    private ConcurrentResult concurrentCreates(String firstAmount,String secondAmount,boolean deterministicLoser) throws Exception {
        UUID firstCampaign=campaign(),secondCampaign=campaign(),firstRequest=UUID.randomUUID(),secondRequest=UUID.randomUUID();CyclicBarrier barrier=new CyclicBarrier(2);CountDownLatch winnerCommitted=new CountDownLatch(1);
        doAnswer(invocation->{barrier.await();if(deterministicLoser&&Thread.currentThread().getName().equals("ledger-loser"))winnerCommitted.await();return null;}).when(hook).beforeAccountDayClaim();
        try(var winner=Executors.newSingleThreadExecutor(r->new Thread(r,"ledger-winner"));var loser=Executors.newSingleThreadExecutor(r->new Thread(r,"ledger-loser"))){Future<Result> one=winner.submit(()->{Result value=create(firstCampaign,firstRequest,firstAmount,"ledger-winner");winnerCommitted.countDown();return value;});Future<Result> two=loser.submit(()->create(secondCampaign,secondRequest,secondAmount,"ledger-loser"));return new ConcurrentResult(one.get(),two.get());}
    }

    private java.util.List<Result> concurrentCreates(java.util.List<String> amounts,int loserIndex) throws Exception {int count=amounts.size();var campaigns=new java.util.ArrayList<UUID>();var requests=new java.util.ArrayList<UUID>();for(int i=0;i<count;i++){campaigns.add(campaign());requests.add(UUID.randomUUID());}CyclicBarrier barrier=new CyclicBarrier(count);CountDownLatch winners=new CountDownLatch(loserIndex<0?0:count-1);doAnswer(invocation->{barrier.await();if(loserIndex>=0&&Thread.currentThread().getName().equals("ledger-worker-"+loserIndex))winners.await();return null;}).when(hook).beforeAccountDayClaim();try(var executor=Executors.newFixedThreadPool(count,r->new Thread(r))){var futures=new java.util.ArrayList<Future<Result>>();for(int i=0;i<count;i++){final int index=i;futures.add(executor.submit(()->{Thread.currentThread().setName("ledger-worker-"+index);Result result=create(campaigns.get(index),requests.get(index),amounts.get(index),"ledger-worker-"+index);if(index!=loserIndex)winners.countDown();return result;}));}var results=new java.util.ArrayList<Result>();for(var future:futures)results.add(future.get());return results;}}

    private UUID campaign(){UUID plan=UUID.randomUUID();jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Concurrency',?,?,'OUTCOME_SALES','META',1000,1000,'TWD')",plan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return transactions.confirmCampaign(UUID.randomUUID(),plan,0,"concurrency-campaign").operation().getEntityUuid();}
    private UUID legacyAdSet(String amount){UUID plan=UUID.randomUUID(),campaign=UUID.randomUUID(),adSet=UUID.randomUUID(),account=UUID.fromString("00000000-0000-4000-8000-00000000005b");jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Legacy decrease',?,?,'OUTCOME_SALES','META',100,300,'TWD')",plan,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,desired_state,schedule_start,schedule_end,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','PAUSED',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei')",campaign,plan,account);jdbc.update("INSERT INTO platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,budget_amount,currency,schedule_start,schedule_end,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key,desired_state) VALUES (?,?,?,'LIFETIME',?,'TWD',current_timestamp+interval '10 days',current_timestamp+interval '20 days','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1','TW_BROAD_FEEDS_V1','PAUSED')",adSet,campaign,account,new BigDecimal(amount));return adSet;}
    private Result create(UUID campaign,UUID request,String amount,String requestId){try{var value=transactions.confirmAdSet(campaign,request,PlatformBudgetType.LIFETIME,new BigDecimal(amount),0,0,requestId).operation();return new Result("SUCCESS",request,value.getEntityUuid());}catch(Stage4BException failure){return new Result(failure.code(),request,null);}}
    private Result budget(UUID adSet,String amount,String requestId){UUID request=UUID.randomUUID();try{var value=transactions.confirmBudget(adSet,request,new BigDecimal(amount),0,requestId).operation();return new Result("SUCCESS",request,value.getEntityUuid());}catch(Stage4BException failure){return new Result(failure.code(),request,null);}}
    private UUID dayUuid(){return jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_account_budget_days",UUID.class);}private long dayVersion(){return jdbc.queryForObject("SELECT version FROM platform_account_budget_days",Long.class);}
    private void assertDayAndRows(String amount,int reservations,int budgetOperations){assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days",BigDecimal.class)).isEqualByComparingTo(amount);assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations",Integer.class)).isEqualTo(reservations);assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE reserved_amount>0",Integer.class)).isEqualTo(budgetOperations);assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_account_budget_days WHERE created_at<=updated_at AND version=(SELECT count(*) FROM platform_budget_reservations WHERE reserved_amount>0)",Integer.class)).isEqualTo(1);}
    private String allRows(){return jdbc.queryForObject("SELECT jsonb_build_object('operations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_uuid) FROM platform_operations t),'attempts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_attempt_uuid) FROM platform_operation_attempts t),'batches',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_batch_uuid) FROM platform_operation_batches t),'reservations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY budget_reservation_uuid) FROM platform_budget_reservations t),'days',(SELECT jsonb_agg(to_jsonb(t) ORDER BY account_budget_day_uuid) FROM platform_account_budget_days t),'campaigns',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_campaign_uuid) FROM platform_campaigns t),'adsets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_ad_set_uuid) FROM platform_ad_sets t),'audit',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid) FROM audit_logs t),'changes',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid,change_order) FROM audit_log_changes t))::text",String.class);}
    private record Result(String code,UUID request,UUID entity){} private record ConcurrentResult(Result first,Result second){}
}
