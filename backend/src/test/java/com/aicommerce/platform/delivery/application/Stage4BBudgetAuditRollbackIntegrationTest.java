package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformBudgetAuditEvent;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired Stage4BTransactions transactions; @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PlatformAuditWriter audit;

    @ParameterizedTest @ValueSource(ints={1,2,3,4,5})
    void createAdSetRollsBackEveryRowWhenTypedAuditAppendFailsAtAnyPosition(int failAt){
        UUID plan=UUID.randomUUID();jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency) VALUES (?,'Audit rollback',?,?, 'OUTCOME_SALES','META',100,300,'TWD')",plan,LocalDate.now().plusDays(2),LocalDate.now().plusDays(5));
        var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"audit-rollback-campaign").operation();
        Snapshot before=snapshot();reset(audit);AtomicInteger calls=new AtomicInteger();org.mockito.stubbing.Answer<Object> answer=invocation->{if(calls.incrementAndGet()==failAt)throw new IllegalStateException("sentinel-audit-failure");return invocation.callRealMethod();};doAnswer(answer).when(audit).write(any(PlatformAuditEvent.class),any(AuditOperationContext.class));doAnswer(answer).when(audit).write(any(PlatformBudgetAuditEvent.class),any(AuditOperationContext.class));
        assertThatThrownBy(()->transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("20"),0,0,"audit-rollback-adset")).isInstanceOf(IllegalStateException.class).hasMessage("sentinel-audit-failure");
        assertThat(snapshot()).isEqualTo(before);assertThat(calls).hasValue(failAt);reset(audit);
    }

    private Snapshot snapshot(){return new Snapshot(count("platform_ad_sets"),count("platform_operations"),count("platform_operation_batches"),count("platform_budget_reservations"),count("platform_account_budget_days"),count("audit_logs"),count("audit_log_changes"));}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private record Snapshot(int adSets,int operations,int batches,int reservations,int days,int audit,int changes){}
}
