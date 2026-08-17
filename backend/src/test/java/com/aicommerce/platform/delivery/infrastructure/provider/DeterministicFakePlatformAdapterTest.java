package com.aicommerce.platform.delivery.infrastructure.provider;
import static org.assertj.core.api.Assertions.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*; import org.junit.jupiter.api.Test;
import com.aicommerce.platform.delivery.application.port.*; import com.aicommerce.platform.delivery.domain.*;
class DeterministicFakePlatformAdapterTest {
 private final UUID operation=UUID.fromString("00000000-0000-0000-0000-000000000001"); private final PlatformCommandIdentity identity=new PlatformCommandIdentity(operation,UUID.randomUUID(),"a".repeat(64),"b".repeat(64));
 @Test void createIsDeterministicAndTransactionFree(){var adapter=new DeterministicFakePlatformAdapter();var command=new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");var first=(WriteSucceeded)adapter.submitCampaign(command);var second=(WriteSucceeded)adapter.submitCampaign(command);assertThat(first.externalId()).isEqualTo(second.externalId());assertThat(first.externalId().orElseThrow()).matches("fake-campaign-[0-9a-f]{24}");assertThat(adapter.transactionObserved()).isFalse();}
 @Test void allCreateEntityPrefixesUseTheSameDeterministicIdentityAlgorithm(){var adapter=new DeterministicFakePlatformAdapter();var campaign=(WriteSucceeded)adapter.submitCampaign(command());var adSet=(WriteSucceeded)adapter.submitAdSet(new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("20"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED));var ad=(WriteSucceeded)adapter.submitAd(new PlatformAdCommand(identity,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),"IMAGE_PRIMARY_V1",PlatformDesiredState.PAUSED));assertThat(campaign.externalId().orElseThrow()).matches("fake-campaign-[0-9a-f]{24}");assertThat(adSet.externalId().orElseThrow()).matches("fake-adset-[0-9a-f]{24}");assertThat(ad.externalId().orElseThrow()).matches("fake-ad-[0-9a-f]{24}");}
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
 @Test void commandMoneyUsesScaleZeroThroughSixAndCampaignCreateIsPausedOnly(){
  var adSet=new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1E+2"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED);
  assertThat(adSet.budgetAmount()).isEqualByComparingTo("100"); assertThat(adSet.budgetAmount().scale()).isZero();
  assertThat(new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1.123456"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED).budgetAmount().scale()).isEqualTo(6);
  assertThatThrownBy(()->new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("1.1234567"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.ACTIVE,Optional.empty(),Optional.empty(),"Asia/Taipei")).isInstanceOf(IllegalArgumentException.class);
 }
 private PlatformCampaignCommand command(){return new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");}
}
