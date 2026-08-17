package com.aicommerce.platform.delivery.domain;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant; import java.util.UUID; import org.junit.jupiter.api.Test;

class PlatformOperationDomainTest {
 @Test void lifecycleRequiresReconciliationAfterUnknownOutcome(){
  PlatformOperation op=operation(3); op.claim(Instant.parse("2026-08-16T00:00:00Z")); op.unknown("trace");
  assertThat(op.getStatus()).isEqualTo(PlatformOperationStatus.UNKNOWN_OUTCOME);
  assertThatThrownBy(()->op.claim(Instant.now())).isInstanceOf(IllegalStateException.class);
  op.reconcileSuccess("fake-1","trace",Instant.parse("2026-08-16T00:01:00Z"));
  assertThat(op.getStatus()).isEqualTo(PlatformOperationStatus.SUCCEEDED);
  assertThatThrownBy(()->op.reconcileFailure("X",null,Instant.now())).isInstanceOf(IllegalStateException.class);
 }
 @Test void retryReusesIdentityAndIsBounded(){
  PlatformOperation op=operation(3); UUID id=op.getOperationUuid(); Instant now=Instant.parse("2026-08-16T00:00:00Z"); op.claim(now); op.failRetryable("RATE_LIMIT",null,now.plusSeconds(10));
  assertThatThrownBy(()->op.claim(now.plusSeconds(9))).isInstanceOf(IllegalStateException.class); op.claim(now.plusSeconds(10));
  assertThat(op.getOperationUuid()).isEqualTo(id); assertThat(op.getAttemptCount()).isEqualTo(2); op.failRetryable("RATE_LIMIT",null,now.plusSeconds(20)); op.claim(now.plusSeconds(20));
  assertThatThrownBy(()->op.failRetryable("RATE_LIMIT",null,Instant.now())).isInstanceOf(IllegalStateException.class);
 }
 @Test void moneyRejectsFloatingPrecisionAndNormalizesScale(){ assertThat(new Money(new java.math.BigDecimal("1.25"),"twd").amount()).isEqualByComparingTo("1.250000"); assertThatThrownBy(()->new Money(new java.math.BigDecimal("1.0000001"),"TWD")).isInstanceOf(IllegalArgumentException.class); }
 private PlatformOperation operation(int max){ return new PlatformOperation(UUID.randomUUID(),UUID.randomUUID(),PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,UUID.randomUUID(),UUID.randomUUID(),"a".repeat(64),"{}","b".repeat(64),"LOCAL_ADMIN","tester","request-1",max); }
}
