package com.aicommerce.platform.delivery.infrastructure.provider;
import java.math.BigDecimal; import java.nio.charset.StandardCharsets; import java.security.*; import java.time.*; import java.time.temporal.ChronoUnit; import java.util.*; import java.util.concurrent.atomic.AtomicInteger;
import com.aicommerce.platform.delivery.application.port.*; import com.aicommerce.platform.delivery.domain.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Component; import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component @Profile("(local | test) & !production") @ConditionalOnProperty(name="platform.adapter",havingValue="fake")
public class DeterministicFakeGooglePlatformAdapter implements PlatformCampaignPort,PlatformAdSetPort,PlatformAdPort,PlatformOperationReconciliationPort,PlatformDeliveryReadPort,PlatformMetricsReadPort {
 private volatile Scenario scenario; private final AtomicInteger invocations=new AtomicInteger(); private volatile boolean transactionObserved; private final Clock clock;
 public DeterministicFakeGooglePlatformAdapter(){this(Scenario.SUCCESS);}
 public DeterministicFakeGooglePlatformAdapter(Scenario scenario){this.scenario=Objects.requireNonNull(scenario);this.clock=Clock.systemUTC();}
 public int invocationCount(){return invocations.get();} public boolean transactionObserved(){return transactionObserved;}
 public void useScenario(Scenario fixture){this.scenario=Objects.requireNonNull(fixture);}
 @Override public ProviderKey providerKey(){return ProviderKey.FAKE_GOOGLE;}
 @Override public PlatformWriteOutcome submitCampaign(PlatformCampaignCommand c){return create(c.identity(),"fake-google-campaign-");}
 @Override public PlatformWriteOutcome submitAdSet(PlatformAdSetCommand c){return create(c.identity(),"fake-google-adset-");}
 @Override public PlatformWriteOutcome submitAd(PlatformAdCommand c){return create(c.identity(),"fake-google-ad-");}
 @Override public PlatformWriteOutcome changeCampaignState(PlatformStateMutationCommand c){return mutation(c.identity(),c.existingExternalId());}
 @Override public PlatformWriteOutcome changeAdSetState(PlatformStateMutationCommand c){return mutation(c.identity(),c.existingExternalId());}
 @Override public PlatformWriteOutcome changeAdState(PlatformStateMutationCommand c){return mutation(c.identity(),c.existingExternalId());}
 @Override public PlatformWriteOutcome updateAdSetBudget(PlatformBudgetMutationCommand c){return mutation(c.identity(),c.existingExternalId());}
 @Override public PlatformReconciliationOutcome reconcile(PlatformReconciliationQuery q){begin();String trace=trace(q.identity().operationUuid());return switch(scenario){
  case RECONCILE_FOUND,SUCCESS -> {boolean create=q.operationType().name().startsWith("CREATE_");Optional<String> id=create?Optional.of(id(q.identity(),prefix(q.entityType()))):Optional.empty();yield new ReconciliationFound(id,Optional.of(trace),Optional.of(PlatformObservedState.PAUSED),evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FOUND,id.map(DeterministicFakeGooglePlatformAdapter::hash),Optional.of(PlatformObservedState.PAUSED),Optional.empty()));}
  case RECONCILE_NOT_FOUND -> new ReconciliationNotFound(Optional.of(trace),evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.NOT_FOUND,Optional.empty(),Optional.empty(),Optional.empty()));
  case RECONCILE_STILL_UNKNOWN -> new ReconciliationStillUnknown(Optional.of(trace),evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.STILL_UNKNOWN,Optional.empty(),Optional.empty(),Optional.empty()));
  default -> new ReconciliationTerminalFailure(PlatformReconciliationTerminalCode.PLATFORM_RECONCILIATION_TERMINAL,Optional.of(trace),evidence(PlatformAttemptKind.RECONCILE,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty()));};}
 @Override public DeliveryObservation readObservedState(DeliveryReadCommand command){Objects.requireNonNull(command);begin();return new DeliveryObservation(fromDesired(command.currentDesiredState()),Optional.empty());}
 @Override public MetricObservation readWindow(MetricReadCommand command){Objects.requireNonNull(command);begin();Instant fetchedAt=Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);return new MetricObservation(Optional.of(10_000L),Optional.of(8_000L),Optional.of(100L),Optional.of(4L),Optional.of(new BigDecimal("25.000000")),Optional.of(new BigDecimal("100.000000")),FreshnessStatus.FRESH,fetchedAt,Optional.empty());}
 private PlatformWriteOutcome create(PlatformCommandIdentity i,String prefix){begin();return outcome(i,Optional.of(id(i,prefix)));}
 private PlatformWriteOutcome mutation(PlatformCommandIdentity i,String existing){begin();if(existing==null||existing.isBlank())throw new IllegalArgumentException("PLATFORM_CONTRACT_INVALID");return outcome(i,Optional.empty());}
 private PlatformWriteOutcome outcome(PlatformCommandIdentity i,Optional<String> id){String trace=trace(i.operationUuid());return switch(scenario){
  case SUCCESS,RECONCILE_FOUND -> new WriteSucceeded(id,Optional.of(trace),Optional.of(PlatformObservedState.PAUSED),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.SUCCEEDED,id.map(DeterministicFakeGooglePlatformAdapter::hash),Optional.of(PlatformObservedState.PAUSED),Optional.empty()));
  case RETRYABLE_RATE_LIMIT -> new WriteRetryableFailure(PlatformRetryableCode.PLATFORM_RATE_LIMITED,60,Optional.of(trace),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,Optional.empty(),Optional.empty(),Optional.of(60)));
  case RETRYABLE_TEMPORARILY_UNAVAILABLE -> new WriteRetryableFailure(PlatformRetryableCode.PLATFORM_TEMPORARILY_UNAVAILABLE,30,Optional.of(trace),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_RETRYABLE,Optional.empty(),Optional.empty(),Optional.of(30)));
  case TERMINAL_VALIDATION -> new WriteTerminalFailure(PlatformWriteTerminalCode.PLATFORM_VALIDATION_FAILED,Optional.of(trace),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty()));
  case TERMINAL_PERMISSION -> new WriteTerminalFailure(PlatformWriteTerminalCode.PLATFORM_PERMISSION_DENIED,Optional.of(trace),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.FAILED_TERMINAL,Optional.empty(),Optional.empty(),Optional.empty()));
  default -> new WriteUnknownOutcome(PlatformUnknownCode.PLATFORM_RESPONSE_AMBIGUOUS,Optional.of(trace),evidence(PlatformAttemptKind.SUBMIT,PlatformEvidenceResultKind.UNKNOWN_OUTCOME,Optional.empty(),Optional.empty(),Optional.empty()));};}
 private void begin(){invocations.incrementAndGet();transactionObserved|=TransactionSynchronizationManager.isActualTransactionActive();if(transactionObserved)throw new IllegalStateException("adapter invoked inside transaction");}
 private static NormalizedPlatformEvidence evidence(PlatformAttemptKind k,PlatformEvidenceResultKind r,Optional<String> f,Optional<PlatformObservedState> o,Optional<Integer> retry){return new NormalizedPlatformEvidence(1,ProviderKey.FAKE_GOOGLE,k,r,f,o,retry);}
 private static String id(PlatformCommandIdentity i,String p){return p+hash("fake-google-platform-id-v1\n"+i.operationUuid().toString().toLowerCase(Locale.ROOT)+"\n"+i.idempotencyKey()+"\n"+i.requestSha256()).substring(0,24);}
 private static String prefix(PlatformEntityType t){return switch(t){case CAMPAIGN->"fake-google-campaign-";case AD_SET->"fake-google-adset-";case AD->"fake-google-ad-";};}
 private static PlatformObservedState fromDesired(PlatformDesiredState desired){return switch(desired){case PAUSED->PlatformObservedState.PAUSED;case ACTIVE->PlatformObservedState.ACTIVE;default->PlatformObservedState.UNKNOWN;};}
 private static String trace(UUID id){return "fake-google-trace-"+hash(id.toString()).substring(0,24);} private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public enum Scenario { SUCCESS,RETRYABLE_RATE_LIMIT,RETRYABLE_TEMPORARILY_UNAVAILABLE,TERMINAL_VALIDATION,TERMINAL_PERMISSION,MALFORMED_RESULT,AMBIGUOUS_TIMEOUT,RECONCILE_FOUND,RECONCILE_NOT_FOUND,RECONCILE_STILL_UNKNOWN,RECONCILE_TERMINAL }
}
