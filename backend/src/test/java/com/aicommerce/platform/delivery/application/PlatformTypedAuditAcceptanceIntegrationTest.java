package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.domain.PlatformAttemptKind;
import com.aicommerce.platform.delivery.domain.PlatformAttemptStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.PlatformOperationStatus;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PlatformTypedAuditAcceptanceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PlatformAuditWriter audit;

    @Test
    void successfulCreateEmitsExactTypedSequenceContentAndPersistedChangeOrder() {
        Fixture fixture=fixture(); reset(audit);
        var context=contexts.forCurrentActor("typed-audit-"+fixture.operationUuid());
        var created=service.create(fixture.command(),context);
        service.submit(fixture.operationUuid(),created.getVersion());

        var captor=ArgumentCaptor.forClass(PlatformAuditEvent.class);
        verify(audit,times(7)).write(captor.capture(),any());
        var events=captor.getAllValues();
        assertThat(events).extracting(PlatformAuditEvent::eventKind).containsExactly(
                PlatformAuditEventKind.ENTITY_CREATED,PlatformAuditEventKind.OPERATION_CREATED,
                PlatformAuditEventKind.ATTEMPT_CREATED,PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ATTEMPT_FINALIZED,PlatformAuditEventKind.OPERATION_TRANSITIONED,
                PlatformAuditEventKind.ENTITY_RESULT_APPLIED);
        assertThat(events).allSatisfy(event->{
            assertThat(event.operationUuid()).isEqualTo(fixture.operationUuid());
            assertThat(event.operationType()).isEqualTo(PlatformOperationType.CREATE_CAMPAIGN);
            assertThat(event.entityType()).isEqualTo(PlatformEntityType.CAMPAIGN);
            assertThat(event.entityUuid()).isEqualTo(fixture.platformCampaignUuid());
        });
        PlatformAuditEvent entityCreated=events.get(0);
        assertThat(entityCreated.subjectType()).isEqualTo(PlatformAuditSubjectType.PLATFORM_CAMPAIGN);
        assertThat(entityCreated.subjectUuid()).isEqualTo(fixture.platformCampaignUuid());
        assertThat(entityCreated.previousDesiredState()).isEmpty();
        assertThat(entityCreated.newDesiredState()).contains(PlatformDesiredState.PAUSED);
        PlatformAuditEvent operationCreated=events.get(1);
        assertThat(operationCreated.previousOperationStatus()).isEmpty();
        assertThat(operationCreated.newOperationStatus()).contains(PlatformOperationStatus.CREATED);
        PlatformAuditEvent attemptCreated=events.get(2);
        assertThat(attemptCreated.attemptKind()).contains(PlatformAttemptKind.SUBMIT);
        assertThat(attemptCreated.attemptNumber()).contains(1);
        assertThat(attemptCreated.previousAttemptStatus()).isEmpty();
        assertThat(attemptCreated.newAttemptStatus()).contains(PlatformAttemptStatus.STARTED);
        assertThat(events.get(3).previousOperationStatus()).contains(PlatformOperationStatus.CREATED);
        assertThat(events.get(3).newOperationStatus()).contains(PlatformOperationStatus.SUBMITTING);
        assertThat(events.get(4).previousAttemptStatus()).contains(PlatformAttemptStatus.STARTED);
        assertThat(events.get(4).newAttemptStatus()).contains(PlatformAttemptStatus.SUCCEEDED);
        assertThat(events.get(5).previousOperationStatus()).contains(PlatformOperationStatus.SUBMITTING);
        assertThat(events.get(5).newOperationStatus()).contains(PlatformOperationStatus.SUCCEEDED);
        PlatformAuditEvent applied=events.get(6);
        assertThat(applied.previousObservedState()).isEmpty();
        assertThat(applied.newObservedState()).contains(PlatformObservedState.PAUSED);
        assertThat(applied.externalIdFingerprint()).hasValueSatisfying(value->assertThat(value).matches("[0-9a-f]{64}"));

        assertThat(changes(fixture.operationUuid(),"PLATFORM_CAMPAIGN","CREATE"))
                .containsExactly("0:desiredState:null->PAUSED");
        assertThat(changes(fixture.operationUuid(),"PLATFORM_OPERATION_ATTEMPT","CREATE"))
                .containsExactly("0:attemptKind:null->SUBMIT","1:attemptNumber:null->1","2:attemptStatus:null->STARTED");
        assertThat(changes(fixture.operationUuid(),"PLATFORM_OPERATION_ATTEMPT","UPDATE"))
                .containsExactly("0:attemptKind:null->SUBMIT","1:attemptNumber:null->1","2:attemptStatus:STARTED->SUCCEEDED","3:safeProviderTraceId:null->"+events.get(4).safeProviderTraceId().orElseThrow());
        assertThat(changes(fixture.operationUuid(),"PLATFORM_CAMPAIGN","UPDATE"))
                .containsExactly("0:observedState:null->PAUSED","1:externalIdFingerprint:null->"+applied.externalIdFingerprint().orElseThrow());
    }

    @Test
    void replayAndInvalidEntryEmitNoTypedOrPersistedEvents() {
        Fixture fixture=fixture();var context=contexts.forCurrentActor("typed-no-event-"+fixture.operationUuid());
        var created=service.create(fixture.command(),context);int persisted=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,fixture.operationUuid());
        reset(audit);service.create(fixture.command(),context);verifyNoInteractions(audit);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,fixture.operationUuid())).isEqualTo(persisted);
        assertThatThrownBy(()->service.submit(fixture.operationUuid(),created.getVersion()+1)).isInstanceOf(PlatformOperationException.class);
        verifyNoInteractions(audit);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,fixture.operationUuid())).isEqualTo(persisted);
    }

    private java.util.List<String> changes(UUID operation,String subject,String action){return jdbc.queryForList("select c.change_order||':'||c.field_name||':'||coalesce(c.old_value,'null')||'->'||coalesce(c.new_value,'null') from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? and l.action=? order by c.change_order",String.class,operation,subject,action);}
    private Fixture fixture(){UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),operation=UUID.randomUUID();jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"typed-"+account,account.toString().replace("-","").repeat(2));jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values (?,?,'ACTIVE',0)",plan,"Typed Audit");String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";return new Fixture(operation,campaign,new CreatePlatformOperationCommand(operation,account,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,campaign,UUID.randomUUID(),payload,3));}
    private record Fixture(UUID operationUuid,UUID platformCampaignUuid,CreatePlatformOperationCommand command){}
}
