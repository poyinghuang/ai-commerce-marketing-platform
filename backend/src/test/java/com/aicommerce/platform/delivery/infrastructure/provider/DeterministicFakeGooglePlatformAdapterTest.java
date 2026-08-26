package com.aicommerce.platform.delivery.infrastructure.provider;
import static org.assertj.core.api.Assertions.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*; import org.junit.jupiter.api.Test;
import com.aicommerce.platform.delivery.application.port.*; import com.aicommerce.platform.delivery.domain.*;
class DeterministicFakeGooglePlatformAdapterTest {
 private final UUID operation=UUID.fromString("00000000-0000-0000-0000-000000000001"); private final PlatformCommandIdentity identity=new PlatformCommandIdentity(operation,UUID.randomUUID(),"a".repeat(64),"b".repeat(64));
 private static final String CAMPAIGN_ID="fake-google-campaign-0fd9fa6c6ed60735aa4d82b9",TRACE_ID="fake-google-trace-7ac1b8d7010bb6cd3a3e84e7",CAMPAIGN_FINGERPRINT="09cfbd048fa7d125c5398d21ed458f7374beba04268ee064ddb4341fb38ccb14";
 @Test void createIsDeterministicPausedAndTransactionFreeWithFakeGoogleEvidence(){
  var adapter=new DeterministicFakeGooglePlatformAdapter();
  assertThat(adapter.providerKey()).isEqualTo(ProviderKey.FAKE_GOOGLE);
  var command=new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");
  var first=(WriteSucceeded)adapter.submitCampaign(command);
  var second=(WriteSucceeded)adapter.submitCampaign(command);
  assertThat(first.externalId()).isEqualTo(second.externalId()).contains(CAMPAIGN_ID);
  assertThat(first.externalId().orElseThrow()).matches("fake-google-campaign-[0-9a-f]{24}");
  assertThat(first.observedState()).contains(PlatformObservedState.PAUSED);
  assertThat(first.evidence().providerKey()).isEqualTo(ProviderKey.FAKE_GOOGLE);
  assertThat(adapter.transactionObserved()).isFalse();
 }
 @Test void entityPrefixesAndReadPortsStayOpaqueAndOffline(){
  var adapter=new DeterministicFakeGooglePlatformAdapter();
  var campaign=(WriteSucceeded)adapter.submitCampaign(command());
  var adSet=(WriteSucceeded)adapter.submitAdSet(new PlatformAdSetCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("20"),"TWD",Optional.empty(),Optional.empty(),"Asia/Taipei","SALES","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",PlatformDesiredState.PAUSED));
  var ad=(WriteSucceeded)adapter.submitAd(new PlatformAdCommand(identity,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),"IMAGE_PRIMARY_V1",PlatformDesiredState.PAUSED));
  assertThat(campaign.externalId()).contains(CAMPAIGN_ID);
  assertThat(adSet.externalId().orElseThrow()).startsWith("fake-google-adset-");
  assertThat(ad.externalId().orElseThrow()).startsWith("fake-google-ad-");
  assertThat(campaign.safeProviderTraceId()).contains(TRACE_ID);
  assertEvidence(campaign.evidence(),PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,CAMPAIGN_FINGERPRINT,PlatformObservedState.PAUSED,null);
  var delivery=adapter.readObservedState(new PlatformDeliveryReadPort.DeliveryReadCommand(UUID.randomUUID(),PlatformEntityType.CAMPAIGN,UUID.randomUUID(),campaign.externalId().orElseThrow(),PlatformDesiredState.PAUSED));
  assertThat(delivery.observedState()).isEqualTo(PlatformObservedState.PAUSED);
  var metrics=adapter.readWindow(new PlatformMetricsReadPort.MetricReadCommand(UUID.randomUUID(),PlatformEntityType.CAMPAIGN,UUID.randomUUID(),campaign.externalId().orElseThrow(),Instant.parse("2026-08-24T16:00:00Z"),Instant.parse("2026-08-25T16:00:00Z"),"Asia/Taipei",7,1,"TWD"));
  assertThat(metrics.freshnessStatus()).isEqualTo(FreshnessStatus.FRESH);
  assertThat(metrics.spend()).contains(new BigDecimal("25.000000"));
 }
 private PlatformCampaignCommand command(){return new PlatformCampaignCommand(identity,UUID.randomUUID(),UUID.randomUUID(),PlatformObjective.OUTCOME_SALES,PlatformDesiredState.PAUSED,Optional.empty(),Optional.empty(),"Asia/Taipei");}
 private void assertEvidence(NormalizedPlatformEvidence evidence,PlatformAttemptKind attempt,PlatformEvidenceResultKind result,String fingerprint,PlatformObservedState observed,Integer retry){
  assertThat(evidence.schemaVersion()).isEqualTo(1); assertThat(evidence.providerKey()).isEqualTo(ProviderKey.FAKE_GOOGLE); assertThat(evidence.attemptKind()).isEqualTo(attempt); assertThat(evidence.resultKind()).isEqualTo(result);
  assertThat(evidence.externalIdFingerprint()).isEqualTo(Optional.ofNullable(fingerprint));
  assertThat(evidence.observedState()).isEqualTo(Optional.ofNullable(observed)); assertThat(evidence.retryAfterSeconds()).isEqualTo(Optional.ofNullable(retry));
 }
}
