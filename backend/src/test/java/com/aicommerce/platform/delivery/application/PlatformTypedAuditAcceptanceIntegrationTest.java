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
import com.aicommerce.platform.delivery.domain.PlatformEvidenceResultKind;
import com.aicommerce.platform.delivery.domain.PlatformRetryableCode;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import com.aicommerce.platform.delivery.domain.PlatformUnknownCode;
import com.aicommerce.platform.delivery.domain.PlatformWriteTerminalCode;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import com.aicommerce.platform.delivery.infrastructure.provider.DeterministicFakePlatformAdapter;
import com.aicommerce.platform.delivery.application.port.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired PlatformOperationTransactions transactions;
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

    @ParameterizedTest(name="submit audit {0}")
    @ValueSource(strings={"RETRYABLE","TERMINAL","UNKNOWN","ATTEMPT_3"})
    void submitFailureAndAttemptThreeEmitExactTypedSequenceAndContent(String scenario) {
        Fixture fixture=fixture();var context=contexts.forCurrentActor("typed-submit-"+scenario+"-"+fixture.operationUuid());var operation=service.create(fixture.command(),context);
        if("ATTEMPT_3".equals(scenario)){
            for(int number=1;number<=2;number++){
                if(number==1)transactions.claimSubmit(fixture.operationUuid(),operation.getVersion(),Instant.now(),context);else transactions.claimRetry(fixture.operationUuid(),operation.getVersion(),operation.getNextAttemptAt(),context);
                operation=transactions.recordWriteOutcome(fixture.operationUuid(),retryable("audit-retry",1),context);jdbc.queryForObject("select pg_sleep(1.2)",Object.class);
            }
        }
        reset(audit);
        if("ATTEMPT_3".equals(scenario))transactions.claimRetry(fixture.operationUuid(),operation.getVersion(),operation.getNextAttemptAt(),context);else transactions.claimSubmit(fixture.operationUuid(),operation.getVersion(),Instant.now(),context);
        PlatformWriteOutcome outcome=switch(scenario){case "TERMINAL"->terminal("audit-terminal");case "UNKNOWN"->unknown("audit-unknown");default->retryable("audit-retry",1);};
        var completed=transactions.recordWriteOutcome(fixture.operationUuid(),outcome,context);int number="ATTEMPT_3".equals(scenario)?3:1;UUID attempt=attempt(fixture.operationUuid(),"SUBMIT",number);
        String finalStatus=switch(scenario){case "RETRYABLE"->"FAILED_RETRYABLE";case "UNKNOWN"->"UNKNOWN_OUTCOME";default->"FAILED_TERMINAL";};
        String code=switch(scenario){case "RETRYABLE"->"PLATFORM_TEMPORARILY_UNAVAILABLE";case "TERMINAL"->"PLATFORM_VALIDATION_FAILED";case "UNKNOWN"->"PLATFORM_RESPONSE_AMBIGUOUS";default->"PLATFORM_MAX_ATTEMPTS_EXCEEDED";};
        String trace=switch(scenario){case "TERMINAL"->"audit-terminal";case "UNKNOWN"->"audit-unknown";default->"audit-retry";};
        String entry="ATTEMPT_3".equals(scenario)?"FAILED_RETRYABLE":"CREATED";
        assertExactEvents(fixture,List.of(
                attemptContent(fixture,"ATTEMPT_CREATED",attempt,"SUBMIT",number,"-","STARTED","-","-"),
                operationContent(fixture,entry,"SUBMITTING","-","-"),
                attemptContent(fixture,"ATTEMPT_FINALIZED",attempt,"SUBMIT",number,"STARTED",finalStatus,code,trace),
                operationContent(fixture,"SUBMITTING",finalStatus,code,trace)));
        assertThat(completed.getStatus().name()).isEqualTo(finalStatus);
    }

    @ParameterizedTest(name="reconciliation audit {0}")
    @ValueSource(strings={"FOUND","NOT_FOUND","STILL_UNKNOWN","TERMINAL"})
    void reconciliationOutcomesEmitExactTypedSequenceAndContent(String scenario) {
        Fixture fixture=fixture();var context=contexts.forCurrentActor("typed-reconcile-"+scenario+"-"+fixture.operationUuid());var created=service.create(fixture.command(),context);transactions.claimSubmit(fixture.operationUuid(),created.getVersion(),Instant.now(),context);var unknown=transactions.recordWriteOutcome(fixture.operationUuid(),unknown("audit-submit-unknown"),context);reset(audit);var query=transactions.claimReconciliation(fixture.operationUuid(),unknown.getVersion(),Instant.now(),context);
        DeterministicFakePlatformAdapter.Scenario fakeScenario=switch(scenario){case "FOUND"->DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND;case "NOT_FOUND"->DeterministicFakePlatformAdapter.Scenario.RECONCILE_NOT_FOUND;case "STILL_UNKNOWN"->DeterministicFakePlatformAdapter.Scenario.RECONCILE_STILL_UNKNOWN;default->DeterministicFakePlatformAdapter.Scenario.RECONCILE_TERMINAL;};
        PlatformReconciliationOutcome outcome=new DeterministicFakePlatformAdapter(fakeScenario).reconcile(query);var completed=transactions.recordReconciliationOutcome(fixture.operationUuid(),outcome,Instant.now(),context);UUID attempt=attempt(fixture.operationUuid(),"RECONCILE",1);String trace=trace(outcome);String finalStatus=switch(scenario){case "FOUND"->"SUCCEEDED";case "TERMINAL"->"FAILED_TERMINAL";default->"UNKNOWN_OUTCOME";};String attemptStatus="NOT_FOUND".equals(scenario)?"NOT_FOUND":finalStatus;String code=switch(scenario){case "FOUND"->"-";case "NOT_FOUND"->"PLATFORM_RECONCILIATION_NOT_FOUND";case "STILL_UNKNOWN"->"PLATFORM_RECONCILIATION_INCONCLUSIVE";default->"PLATFORM_RECONCILIATION_TERMINAL";};
        var expected=new java.util.ArrayList<String>();expected.add(attemptContent(fixture,"ATTEMPT_CREATED",attempt,"RECONCILE",1,"-","STARTED","-","-"));expected.add(operationContent(fixture,"UNKNOWN_OUTCOME","RECONCILING","-","-"));expected.add(attemptContent(fixture,"ATTEMPT_FINALIZED",attempt,"RECONCILE",1,"STARTED",attemptStatus,code,trace));expected.add(operationContent(fixture,"RECONCILING",finalStatus,code,trace));if("FOUND".equals(scenario))expected.add(entityContent(fixture,"-","-","-","PAUSED","-","-",completed.getExternalId()==null?"-":sha256(completed.getExternalId())));
        assertExactEvents(fixture,expected);assertThat(completed.getStatus().name()).isEqualTo(finalStatus);
    }

    @ParameterizedTest(name="mutation audit {0}")
    @ValueSource(strings={"STATE","BUDGET"})
    void successfulStateAndBudgetMutationsEmitExactTypedSequenceAndContent(String scenario) {
        MutationFixture mutation="STATE".equals(scenario)?stateFixture():budgetFixture();var context=contexts.forCurrentActor("typed-mutation-"+scenario+"-"+mutation.operationUuid());var operation=service.create(mutation.command(),context);reset(audit);var completed=service.submit(mutation.operationUuid(),operation.getVersion());UUID attempt=attempt(mutation.operationUuid(),"SUBMIT",1);Fixture identity=new Fixture(mutation.operationUuid(),mutation.entityUuid(),mutation.command());String trace=completed.safeProviderTraceId().orElseThrow();String entity="STATE".equals(scenario)?entityContent(identity,"PAUSED","ACTIVE","PAUSED","PAUSED","-","-","-"):entityContent(identity,"-","-","PAUSED","PAUSED","20","30","-");
        assertExactEvents(identity,List.of(attemptContent(identity,"ATTEMPT_CREATED",attempt,"SUBMIT",1,"-","STARTED","-","-"),operationContent(identity,"CREATED","SUBMITTING","-","-"),attemptContent(identity,"ATTEMPT_FINALIZED",attempt,"SUBMIT",1,"STARTED","SUCCEEDED","-",trace),operationContent(identity,"SUBMITTING","SUCCEEDED","-",trace),entity));
    }

    @ParameterizedTest(name="recovery audit {0}")
    @ValueSource(strings={"SUBMIT","RECONCILE"})
    void staleRecoveryEmitsExactTypedSequenceAndContent(String kind) {
        Fixture fixture=fixture();var context=contexts.forCurrentActor("typed-recovery-"+kind+"-"+fixture.operationUuid());var created=service.create(fixture.command(),context);transactions.claimSubmit(fixture.operationUuid(),created.getVersion(),Instant.now(),context);var claimed=transactions.get(fixture.operationUuid());if("RECONCILE".equals(kind)){var unknown=transactions.recordWriteOutcome(fixture.operationUuid(),unknown("recovery-submit"),context);transactions.claimReconciliation(fixture.operationUuid(),unknown.getVersion(),Instant.now(),context);claimed=transactions.get(fixture.operationUuid());}reset(audit);String entry=claimed.getStatus().name();int number="SUBMIT".equals(kind)?claimed.getAttemptCount():claimed.getReconciliationCount();UUID attempt=attempt(fixture.operationUuid(),kind,number);String code="SUBMIT".equals(kind)?"PLATFORM_RESPONSE_AMBIGUOUS":"PLATFORM_RECONCILIATION_INCONCLUSIVE";transactions.recoverStaleClaim(fixture.operationUuid(),claimed.getVersion(),claimed.getClaimedAt().plusSeconds(300),context);assertExactEvents(fixture,List.of(attemptContent(fixture,"ATTEMPT_FINALIZED",attempt,kind,number,"STARTED","UNKNOWN_OUTCOME",code,"-"),operationContent(fixture,entry,"UNKNOWN_OUTCOME",code,"-")));
    }

    @ParameterizedTest(name="no-event {0}")
    @ValueSource(strings={"STALE_SUBMIT","INVALID_RECONCILE","RECOVERY_NOT_DUE"})
    void rejectedEntryMatrixEmitsNoTypedOrPersistedAudit(String scenario) {
        Fixture fixture=fixture();var context=contexts.forCurrentActor("typed-no-event-matrix-"+scenario+"-"+fixture.operationUuid());var created=service.create(fixture.command(),context);int persisted=jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,fixture.operationUuid());reset(audit);assertThatThrownBy(()->{switch(scenario){case "STALE_SUBMIT"->service.submit(fixture.operationUuid(),created.getVersion()+1);case "INVALID_RECONCILE"->transactions.claimReconciliation(fixture.operationUuid(),created.getVersion(),Instant.now(),context);default->service.recoverStaleClaim(fixture.operationUuid(),created.getVersion(),Instant.now());}}).isInstanceOf(PlatformOperationException.class);verifyNoInteractions(audit);assertThat(jdbc.queryForObject("select count(*) from audit_logs where operation_uuid=?",Integer.class,fixture.operationUuid())).isEqualTo(persisted);assertThat(jdbc.queryForObject("select count(*) from platform_operation_attempts where operation_uuid=?",Integer.class,fixture.operationUuid())).isZero();
    }

    private java.util.List<String> changes(UUID operation,String subject,String action){return jdbc.queryForList("select c.change_order||':'||c.field_name||':'||coalesce(c.old_value,'null')||'->'||coalesce(c.new_value,'null') from audit_log_changes c join audit_logs l on l.audit_uuid=c.audit_uuid where l.operation_uuid=? and l.entity_type=? and l.action=? order by c.change_order",String.class,operation,subject,action);}
    private void assertExactEvents(Fixture fixture,List<String> expected){var captor=ArgumentCaptor.forClass(PlatformAuditEvent.class);verify(audit,times(expected.size())).write(captor.capture(),any());assertThat(captor.getAllValues()).allSatisfy(event->{assertThat(event.operationUuid()).isEqualTo(fixture.operationUuid());assertThat(event.operationType()).isEqualTo(fixture.command().operationType());assertThat(event.entityType()).isEqualTo(fixture.command().entityType());assertThat(event.entityUuid()).isEqualTo(fixture.platformCampaignUuid());}).extracting(this::content).containsExactlyElementsOf(expected);}
    private String content(PlatformAuditEvent e){return String.join("|",e.eventKind().name(),e.subjectType().name(),e.action().name(),e.subjectUuid().toString(),e.operationType().name(),e.entityType().name(),opt(e.previousOperationStatus()),opt(e.newOperationStatus()),opt(e.attemptKind()),opt(e.attemptNumber()),opt(e.previousAttemptStatus()),opt(e.newAttemptStatus()),opt(e.previousDesiredState()),opt(e.newDesiredState()),opt(e.previousObservedState()),opt(e.newObservedState()),money(e.previousBudgetAmount()),money(e.newBudgetAmount()),opt(e.externalIdFingerprint()),opt(e.normalizedErrorCode()),opt(e.safeProviderTraceId()));}
    private String attemptContent(Fixture fixture,String event,UUID subject,String kind,int number,String before,String after,String code,String trace){return String.join("|",event,"PLATFORM_OPERATION_ATTEMPT",event.equals("ATTEMPT_CREATED")?"CREATE":"UPDATE",subject.toString(),fixture.command().operationType().name(),fixture.command().entityType().name(),"-","-",kind,Integer.toString(number),before,after,"-","-","-","-","-","-","-",code,trace);}
    private String operationContent(Fixture fixture,String before,String after,String code,String trace){return String.join("|","OPERATION_TRANSITIONED","PLATFORM_OPERATION","UPDATE",fixture.operationUuid().toString(),fixture.command().operationType().name(),fixture.command().entityType().name(),before,after,"-","-","-","-","-","-","-","-","-","-","-",code,trace);}
    private String entityContent(Fixture fixture,String desiredBefore,String desiredAfter,String observedBefore,String observedAfter,String budgetBefore,String budgetAfter,String fingerprint){return String.join("|","ENTITY_RESULT_APPLIED",fixture.command().entityType()==PlatformEntityType.AD_SET?"PLATFORM_AD_SET":"PLATFORM_CAMPAIGN","UPDATE",fixture.platformCampaignUuid().toString(),fixture.command().operationType().name(),fixture.command().entityType().name(),"-","-","-","-","-","-",desiredBefore,desiredAfter,observedBefore,observedAfter,budgetBefore,budgetAfter,fingerprint,"-","-");}
    private String opt(Optional<?> value){return value.map(v->v instanceof Enum<?> x?x.name():v.toString()).orElse("-");}private String money(Optional<BigDecimal> value){return value.map(BigDecimal::toPlainString).orElse("-");}
    private UUID attempt(UUID operation,String kind,int number){return jdbc.queryForObject("select operation_attempt_uuid from platform_operation_attempts where operation_uuid=? and attempt_kind=? and attempt_number=?",UUID.class,operation,kind,number);}
    private WriteRetryableFailure retryable(String trace,int seconds){return new WriteRetryableFailure(PlatformRetryableCode.PLATFORM_TEMPORARILY_UNAVAILABLE,seconds,Optional.of(trace),new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,Optional.empty(),Optional.empty(),Optional.of(seconds)));}
    private WriteTerminalFailure terminal(String trace){return new WriteTerminalFailure(PlatformWriteTerminalCode.PLATFORM_VALIDATION_FAILED,Optional.of(trace),new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty()));}
    private WriteUnknownOutcome unknown(String trace){return new WriteUnknownOutcome(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS,Optional.of(trace),new NormalizedPlatformEvidence(1,ProviderKey.FAKE,PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.UNKNOWN_OUTCOME,Optional.empty(),Optional.empty(),Optional.empty()));}
    private String trace(PlatformReconciliationOutcome outcome){return switch(outcome){case ReconciliationFound x->x.safeProviderTraceId().orElseThrow();case ReconciliationNotFound x->x.safeProviderTraceId().orElseThrow();case ReconciliationStillUnknown x->x.safeProviderTraceId().orElseThrow();case ReconciliationTerminalFailure x->x.safeProviderTraceId().orElseThrow();};}
    private String sha256(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new AssertionError(e);}}
    private MutationFixture stateFixture(){Fixture created=fixture();var context=contexts.forCurrentActor("typed-state-create-"+created.operationUuid());var operation=service.create(created.command(),context);service.submit(created.operationUuid(),operation.getVersion());UUID id=UUID.randomUUID();String payload="{\"schemaVersion\":1,\"operationType\":\"RESUME\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+created.platformCampaignUuid()+"\",\"expectedEntityVersion\":1,\"targetDesiredState\":\"ACTIVE\"}";return new MutationFixture(id,created.platformCampaignUuid(),new CreatePlatformOperationCommand(id,created.command().platformAccountUuid(),PlatformOperationType.RESUME,PlatformEntityType.CAMPAIGN,created.platformCampaignUuid(),UUID.randomUUID(),payload,3));}
    private MutationFixture budgetFixture(){Fixture campaign=fixture();var context=contexts.forCurrentActor("typed-budget-campaign-"+campaign.operationUuid());var operation=service.create(campaign.command(),context);service.submit(campaign.operationUuid(),operation.getVersion());UUID adSet=UUID.randomUUID(),createId=UUID.randomUUID();String createPayload="{\"schemaVersion\":1,\"operationType\":\"CREATE_AD_SET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+adSet+"\",\"platformAdSetUuid\":\""+adSet+"\",\"platformCampaignUuid\":\""+campaign.platformCampaignUuid()+"\",\"budgetType\":\"DAILY\",\"budgetAmount\":20,\"currency\":\"TWD\",\"accountTimezone\":\"Asia/Taipei\",\"optimizationGoal\":\"OFFSITE_CONVERSIONS\",\"targetingProfileKey\":\"TW_BROAD_FEEDS_V1\",\"placementProfileKey\":\"TW_BROAD_FEEDS_V1\",\"desiredState\":\"PAUSED\"}";var create=service.create(new CreatePlatformOperationCommand(createId,campaign.command().platformAccountUuid(),PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,adSet,UUID.randomUUID(),createPayload,3),contexts.forCurrentActor("typed-budget-adset-"+createId));service.submit(createId,create.getVersion());UUID id=UUID.randomUUID();String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+adSet+"\",\"platformAdSetUuid\":\""+adSet+"\",\"expectedEntityVersion\":1,\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":20,\"newBudgetAmount\":30}";return new MutationFixture(id,adSet,new CreatePlatformOperationCommand(id,campaign.command().platformAccountUuid(),PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,adSet,UUID.randomUUID(),payload,3));}
    private Fixture fixture(){UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),operation=UUID.randomUUID();jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"typed-"+account,account.toString().replace("-","").repeat(2));jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values (?,?,'ACTIVE',0)",plan,"Typed Audit");String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";return new Fixture(operation,campaign,new CreatePlatformOperationCommand(operation,account,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,campaign,UUID.randomUUID(),payload,3));}
    private record Fixture(UUID operationUuid,UUID platformCampaignUuid,CreatePlatformOperationCommand command){}
    private record MutationFixture(UUID operationUuid,UUID entityUuid,CreatePlatformOperationCommand command){}
}
