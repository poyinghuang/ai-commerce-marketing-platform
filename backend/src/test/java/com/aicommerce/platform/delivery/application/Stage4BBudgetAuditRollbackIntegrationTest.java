package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformBudgetAuditEvent;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
class Stage4BBudgetAuditRollbackIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired Stage4BTransactions transactions; @Autowired PlatformOperationService operations; @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PlatformAuditWriter audit;
    @MockitoSpyBean Stage4BLedgerCriticalSectionHook transactionHook;

    @ParameterizedTest(name="{0} audit append {1}") @MethodSource("auditFailurePositions")
    void everyStage4BCommandRollsBackItsCompleteDatabaseStateWhenAuditAppendFails(String command,int failAt){
        Runnable mutation=fixture(command);
        Snapshot before=snapshot();reset(audit);AtomicInteger calls=new AtomicInteger();org.mockito.stubbing.Answer<Object> answer=invocation->{if(calls.incrementAndGet()==failAt)throw new IllegalStateException("sentinel-audit-failure");return invocation.callRealMethod();};doAnswer(answer).when(audit).write(any(PlatformAuditEvent.class),any(AuditOperationContext.class));doAnswer(answer).when(audit).write(any(PlatformBudgetAuditEvent.class),any(AuditOperationContext.class));
        assertThatThrownBy(mutation::run).isInstanceOf(IllegalStateException.class).hasMessage("sentinel-audit-failure");
        assertThat(snapshot()).as("complete database state for %s failure at append %s",command,failAt).isEqualTo(before);assertThat(calls).hasValue(failAt);reset(audit);
    }

    static Stream<Arguments> auditFailurePositions(){return Stream.of(
        positions("CREATE_CAMPAIGN",3),positions("CREATE_AD_SET",5),positions("STATE",2),positions("BUDGET_INCREASE",4),positions("BUDGET_DECREASE",3)).flatMap(s->s);
    }

    @ParameterizedTest(name="{0} post-audit pre-commit rollback")
    @MethodSource("commands")
    void everyStage4BCommandRollsBackAfterFinalSuccessfulAuditAppendBeforeCommit(String command){
        Runnable mutation=fixture(command);Snapshot before=snapshot();reset(transactionHook);
        doThrow(new IllegalStateException("sentinel-before-commit")).when(transactionHook).afterAuditAppends();
        assertThatThrownBy(mutation::run).isInstanceOf(IllegalStateException.class).hasMessage("sentinel-before-commit");
        assertThat(snapshot()).as("complete database state for %s after final audit append",command).isEqualTo(before);
        verify(transactionHook).afterAuditAppends();reset(transactionHook);
    }

    static Stream<String> commands(){return Stream.of("CREATE_CAMPAIGN","CREATE_AD_SET","STATE","BUDGET_INCREASE","BUDGET_DECREASE");}
    private static Stream<Arguments> positions(String command,int count){return IntStream.rangeClosed(1,count).mapToObj(position->Arguments.of(command,position));}

    private Runnable fixture(String command){
        UUID plan=UUID.randomUUID();jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Audit rollback',?,?, 'OUTCOME_SALES','META',100,300,'TWD')",plan,LocalDate.now().plusDays(2),LocalDate.now().plusDays(5));
        if(command.equals("CREATE_CAMPAIGN"))return ()->transactions.confirmCampaign(UUID.randomUUID(),plan,0,"audit-rollback-campaign");
        var campaignCreation=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"audit-fixture-campaign").operation();UUID campaign=campaignCreation.getEntityUuid();operations.submit(campaignCreation.getOperationUuid(),0);
        if(command.equals("CREATE_AD_SET"))return ()->transactions.confirmAdSet(campaign,UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("20"),0,1,"audit-rollback-adset");
        if(command.equals("STATE"))return ()->transactions.confirmState(com.aicommerce.platform.delivery.domain.PlatformEntityType.CAMPAIGN,campaign,UUID.randomUUID(),com.aicommerce.platform.delivery.domain.PlatformDesiredState.ACTIVE,1,"audit-rollback-state");
        BigDecimal initial=command.equals("BUDGET_DECREASE")?new BigDecimal("30"):new BigDecimal("20");
        var adSetCreation=transactions.confirmAdSet(campaign,UUID.randomUUID(),PlatformBudgetType.DAILY,initial,0,1,"audit-fixture-adset").operation();UUID adSet=adSetCreation.getEntityUuid();operations.submit(adSetCreation.getOperationUuid(),0);
        BigDecimal next=command.equals("BUDGET_DECREASE")?new BigDecimal("20"):new BigDecimal("30");
        return ()->transactions.confirmBudget(adSet,UUID.randomUUID(),next,1,"audit-rollback-budget");
    }

    private Snapshot snapshot(){return new Snapshot(rows("platform_campaigns"),rows("platform_ad_sets"),rows("platform_operations"),rows("platform_operation_attempts"),rows("platform_operation_batches"),rows("platform_budget_reservations"),rows("platform_account_budget_days"),rows("audit_logs"),rows("audit_log_changes"));}
    private String rows(String table){return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class);}
    private record Snapshot(String campaigns,String adSets,String operations,String attempts,String batches,String reservations,String days,String audit,String changes){}
}
