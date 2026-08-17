package com.aicommerce.platform.delivery.infrastructure.provider;
import static org.assertj.core.api.Assertions.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*; import org.junit.jupiter.api.Test;
import com.aicommerce.platform.delivery.application.port.*; import com.aicommerce.platform.delivery.domain.*;
class DeterministicFakePlatformAdapterTest {
 private final UUID operation=UUID.fromString("00000000-0000-0000-0000-000000000001"); private final PlatformCommandIdentity identity=new PlatformCommandIdentity(operation,UUID.randomUUID(),"a".repeat(64),"b".repeat(64));
 private static final String CAMPAIGN_ID="fake-campaign-ade594cf975ac13eef1078b4",TRACE_ID="fake-trace-7ac1b8d7010bb6cd3a3e84e7",CAMPAIGN_FINGERPRINT="d613286edc6990b14daae3bedb1b1abcbf6e1a4765d64118e3eae6b1066d5584";
 @Test void createIsDeterministicAndTransactionFree(){var adapter=new DeterministicFakePlatformAdapter();var command=new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");var first=(WriteSucceeded)adapter.submitCampaign(command);var second=(WriteSucceeded)adapter.submitCampaign(command);assertThat(first.externalId()).isEqualTo(second.externalId());assertThat(first.externalId().orElseThrow()).matches("fake-campaign-[0-9a-f]{24}");assertThat(adapter.transactionObserved()).isFalse();}
 @Test void allCreateEntityPrefixesUseTheSameDeterministicIdentityAlgorithm(){var adapter=new DeterministicFakePlatformAdapter();var campaignCommand=command();var adSetCommand=new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("20"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED);var adCommand=new PlatformAdCommand(identity,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),"IMAGE_PRIMARY_V1",PlatformDesiredState.PAUSED);var campaign=(WriteSucceeded)adapter.submitCampaign(campaignCommand);var adSet=(WriteSucceeded)adapter.submitAdSet(adSetCommand);var ad=(WriteSucceeded)adapter.submitAd(adCommand);assertThat(campaign.externalId()).contains(CAMPAIGN_ID);assertThat(adSet.externalId()).contains("fake-adset-ade594cf975ac13eef1078b4");assertThat(ad.externalId()).contains("fake-ad-ade594cf975ac13eef1078b4");assertThat(((WriteSucceeded)adapter.submitCampaign(campaignCommand)).externalId()).isEqualTo(campaign.externalId());assertThat(((WriteSucceeded)adapter.submitAdSet(adSetCommand)).externalId()).isEqualTo(adSet.externalId());assertThat(((WriteSucceeded)adapter.submitAd(adCommand)).externalId()).isEqualTo(ad.externalId());}
 @Test void mutationReturnsNoIdOrFingerprint(){var adapter=new DeterministicFakePlatformAdapter();var out=(WriteSucceeded)adapter.changeCampaignState(new PlatformStateMutationCommand(identity,PlatformEntityType.CAMPAIGN,UUID.randomUUID(),"fake-campaign-abc",0,PlatformDesiredState.ACTIVE));assertThat(out.externalId()).isEmpty();assertThat(out.evidence().externalIdFingerprint()).isEmpty();}
 @Test void allSubmitFixturesAreStable(){
  Map<DeterministicFakePlatformAdapter.Scenario,Class<?>> expected=Map.of(
    DeterministicFakePlatformAdapter.Scenario.SUCCESS,WriteSucceeded.class,
    DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND,WriteSucceeded.class,
    DeterministicFakePlatformAdapter.Scenario.RETRYABLE_RATE_LIMIT,WriteRetryableFailure.class,
    DeterministicFakePlatformAdapter.Scenario.RETRYABLE_TEMPORARILY_UNAVAILABLE,WriteRetryableFailure.class,
    DeterministicFakePlatformAdapter.Scenario.TERMINAL_VALIDATION,WriteTerminalFailure.class,
    DeterministicFakePlatformAdapter.Scenario.TERMINAL_PERMISSION,WriteTerminalFailure.class,
    DeterministicFakePlatformAdapter.Scenario.MALFORMED_RESULT,WriteUnknownOutcome.class,
    DeterministicFakePlatformAdapter.Scenario.AMBIGUOUS_TIMEOUT,WriteUnknownOutcome.class);
  expected.forEach((scenario,type)->assertThat(new DeterministicFakePlatformAdapter(scenario).submitCampaign(command())).as(scenario.name()).isInstanceOf(type));
 }
 @Test void allReconciliationFixturesAreStable(){
  Map<DeterministicFakePlatformAdapter.Scenario,Class<?>> expected=Map.of(
    DeterministicFakePlatformAdapter.Scenario.SUCCESS,ReconciliationFound.class,
    DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND,ReconciliationFound.class,
    DeterministicFakePlatformAdapter.Scenario.RECONCILE_NOT_FOUND,ReconciliationNotFound.class,
    DeterministicFakePlatformAdapter.Scenario.RECONCILE_STILL_UNKNOWN,ReconciliationStillUnknown.class,
    DeterministicFakePlatformAdapter.Scenario.RECONCILE_TERMINAL,ReconciliationTerminalFailure.class);
  var query=new PlatformReconciliationQuery(identity,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,UUID.randomUUID(),1,1,Optional.empty());
  expected.forEach((scenario,type)->assertThat(new DeterministicFakePlatformAdapter(scenario).reconcile(query)).as(scenario.name()).isInstanceOf(type));
 }
 @Test void everyFixtureReturnsExactNormalizedCodeTraceIdRetryAndEvidenceFields(){
  var success=(WriteSucceeded)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.SUCCESS).submitCampaign(command());
  assertThat(success.externalId()).contains(CAMPAIGN_ID); assertThat(success.safeProviderTraceId()).contains(TRACE_ID); assertThat(success.observedState()).contains(PlatformObservedState.PAUSED);
  assertEvidence(success.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,CAMPAIGN_FINGERPRINT,PlatformObservedState.PAUSED,null);
  var submitReconcileFound=(WriteSucceeded)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND).submitCampaign(command());
  assertThat(submitReconcileFound.externalId()).contains(CAMPAIGN_ID); assertThat(submitReconcileFound.safeProviderTraceId()).contains(TRACE_ID); assertThat(submitReconcileFound.observedState()).contains(PlatformObservedState.PAUSED);
  assertEvidence(submitReconcileFound.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,CAMPAIGN_FINGERPRINT,PlatformObservedState.PAUSED,null);

  var rate=(WriteRetryableFailure)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RETRYABLE_RATE_LIMIT).submitCampaign(command());
  assertThat(rate.errorCode()).isEqualTo(PlatformRetryableCode.PLATFORM_RATE_LIMITED); assertThat(rate.retryAfterSeconds()).isEqualTo(60); assertThat(rate.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(rate.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,null,null,60);
  var unavailable=(WriteRetryableFailure)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RETRYABLE_TEMPORARILY_UNAVAILABLE).submitCampaign(command());
  assertThat(unavailable.errorCode()).isEqualTo(PlatformRetryableCode.PLATFORM_TEMPORARILY_UNAVAILABLE); assertThat(unavailable.retryAfterSeconds()).isEqualTo(30); assertThat(unavailable.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(unavailable.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,null,null,30);

  var validation=(WriteTerminalFailure)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.TERMINAL_VALIDATION).submitCampaign(command());
  assertThat(validation.errorCode()).isEqualTo(PlatformWriteTerminalCode.PLATFORM_VALIDATION_FAILED); assertThat(validation.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(validation.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,null,null,null);
  var permission=(WriteTerminalFailure)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.TERMINAL_PERMISSION).submitCampaign(command());
  assertThat(permission.errorCode()).isEqualTo(PlatformWriteTerminalCode.PLATFORM_PERMISSION_DENIED); assertThat(permission.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(permission.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,null,null,null);
  for(var scenario:List.of(DeterministicFakePlatformAdapter.Scenario.MALFORMED_RESULT,DeterministicFakePlatformAdapter.Scenario.AMBIGUOUS_TIMEOUT)){
   var unknown=(WriteUnknownOutcome)new DeterministicFakePlatformAdapter(scenario).submitCampaign(command());
   assertThat(unknown.errorCode()).isEqualTo(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS); assertThat(unknown.safeProviderTraceId()).contains(TRACE_ID);
   assertEvidence(unknown.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.UNKNOWN_OUTCOME,null,null,null);
  }

  var query=new PlatformReconciliationQuery(identity,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,UUID.randomUUID(),1,1,Optional.empty());
  var found=(ReconciliationFound)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RECONCILE_FOUND).reconcile(query);
  assertThat(found.externalId()).contains(CAMPAIGN_ID); assertThat(found.safeProviderTraceId()).contains(TRACE_ID); assertThat(found.observedState()).contains(PlatformObservedState.PAUSED);
  assertEvidence(found.evidence(),PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,CAMPAIGN_FINGERPRINT,PlatformObservedState.PAUSED,null);
  var successFound=(ReconciliationFound)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.SUCCESS).reconcile(query);
  assertThat(successFound.externalId()).contains(CAMPAIGN_ID); assertThat(successFound.safeProviderTraceId()).contains(TRACE_ID); assertThat(successFound.observedState()).contains(PlatformObservedState.PAUSED);
  assertEvidence(successFound.evidence(),PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,CAMPAIGN_FINGERPRINT,PlatformObservedState.PAUSED,null);
  var notFound=(ReconciliationNotFound)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RECONCILE_NOT_FOUND).reconcile(query);
  assertThat(notFound.safeProviderTraceId()).contains(TRACE_ID); assertEvidence(notFound.evidence(),PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.NOT_FOUND,null,null,null);
  var stillUnknown=(ReconciliationStillUnknown)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RECONCILE_STILL_UNKNOWN).reconcile(query);
  assertThat(stillUnknown.safeProviderTraceId()).contains(TRACE_ID); assertEvidence(stillUnknown.evidence(),PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.STILL_UNKNOWN,null,null,null);
  var terminal=(ReconciliationTerminalFailure)new DeterministicFakePlatformAdapter(DeterministicFakePlatformAdapter.Scenario.RECONCILE_TERMINAL).reconcile(query);
  assertThat(terminal.errorCode()).isEqualTo(PlatformReconciliationTerminalCode.PLATFORM_RECONCILIATION_TERMINAL); assertThat(terminal.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(terminal.evidence(),PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FAILED_TERMINAL,null,null,null);
 }
 @Test void commandMoneyUsesScaleZeroThroughSixAndCampaignCreateIsPausedOnly(){
  var adSet=new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1E+2"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED);
  assertThat(adSet.budgetAmount()).isEqualByComparingTo("100"); assertThat(adSet.budgetAmount().scale()).isZero();
  assertThat(new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1.123456"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED).budgetAmount().scale()).isEqualTo(6);
  assertThatThrownBy(()->new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1.1234567"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.ACTIVE,Optional.empty(),Optional.empty(),"Asia/Taipei")).isInstanceOf(IllegalArgumentException.class);
 }
 private PlatformCampaignCommand command(){return new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");}
 private void assertEvidence(NormalizedPlatformEvidence evidence,PlatformAttemptKind attempt,PlatformEvidenceResultKind result,String fingerprint,PlatformObservedState observed,Integer retry){
  assertThat(evidence.schemaVersion()).isEqualTo(1); assertThat(evidence.providerKey()).isEqualTo(ProviderKey.FAKE); assertThat(evidence.attemptKind()).isEqualTo(attempt); assertThat(evidence.resultKind()).isEqualTo(result);
  assertThat(evidence.externalIdFingerprint()).isEqualTo(Optional.ofNullable(fingerprint));
  assertThat(evidence.observedState()).isEqualTo(Optional.ofNullable(observed)); assertThat(evidence.retryAfterSeconds()).isEqualTo(Optional.ofNullable(retry));
 }
}
