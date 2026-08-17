package com.aicommerce.platform.delivery.infrastructure.audit;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.delivery.application.audit.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Component
public final class DefaultPlatformAuditWriter implements PlatformAuditWriter {
 private final AuditWriter writer;private final AuditOperationContextFactory contexts;private final Clock clock;private final JdbcTemplate jdbc;
 public DefaultPlatformAuditWriter(AuditWriter writer,AuditOperationContextFactory contexts,Clock clock,JdbcTemplate jdbc){this.writer=writer;this.contexts=contexts;this.clock=clock;this.jdbc=jdbc;}
 @Override public void write(PlatformAuditEvent e,AuditOperationContext context){var c=new ArrayList<AuditChange>();add(c,"operationStatus",e.previousOperationStatus().map(Enum::name).orElse(null),e.newOperationStatus().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"attemptKind",null,e.attemptKind().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"attemptNumber",null,e.attemptNumber().map(String::valueOf).orElse(null),AuditValueType.INTEGER);add(c,"attemptStatus",e.previousAttemptStatus().map(Enum::name).orElse(null),e.newAttemptStatus().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"desiredState",e.previousDesiredState().map(Enum::name).orElse(null),e.newDesiredState().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"observedState",e.previousObservedState().map(Enum::name).orElse(null),e.newObservedState().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"budgetAmount",e.previousBudgetAmount().map(BigDecimal::toPlainString).orElse(null),e.newBudgetAmount().map(BigDecimal::toPlainString).orElse(null),AuditValueType.DECIMAL);add(c,"externalIdFingerprint",null,e.externalIdFingerprint().orElse(null),AuditValueType.STRING);add(c,"normalizedErrorCode",null,e.normalizedErrorCode().map(Enum::name).orElse(null),AuditValueType.STRING);add(c,"safeProviderTraceId",null,e.safeProviderTraceId().orElse(null),AuditValueType.STRING);UUID product=e.subjectType()==PlatformAuditSubjectType.PLATFORM_AD?jdbc.queryForObject("SELECT product_uuid FROM platform_ads WHERE platform_ad_uuid=?",UUID.class,e.subjectUuid()):null;writer.append(new AuditEvent(UUID.randomUUID(),contexts.forStableOperation(e.operationUuid(),context),e.action(),e.subjectType().name(),e.subjectUuid(),product,Instant.now(clock),c));}
 @Override public void write(PlatformBudgetAuditEvent e,AuditOperationContext context){var c=new ArrayList<AuditChange>();add(c,"operationBatchUuid",null,e.operationBatchUuid().toString(),AuditValueType.UUID);add(c,"budgetReservationUuid",null,e.budgetReservationUuid().map(UUID::toString).orElse(null),AuditValueType.UUID);add(c,"accountBudgetDayUuid",null,e.accountBudgetDayUuid().map(UUID::toString).orElse(null),AuditValueType.UUID);add(c,"businessDate",null,e.businessDate().toString(),AuditValueType.DATE);add(c,"reservationKind",null,e.reservationKind().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"currency",null,e.currency(),AuditValueType.STRING);add(c,"budgetAmount",e.previousBudgetAmount().map(BigDecimal::toPlainString).orElse(null),e.newBudgetAmount().map(BigDecimal::toPlainString).orElse(null),AuditValueType.DECIMAL);add(c,"reservedAmount",null,e.reservedAmount().toPlainString(),AuditValueType.DECIMAL);add(c,"aggregateReservedAmount",e.previousAggregateAmount().map(BigDecimal::toPlainString).orElse(null),e.newAggregateAmount().map(BigDecimal::toPlainString).orElse(null),AuditValueType.DECIMAL);writer.append(new AuditEvent(UUID.randomUUID(),contexts.forStableOperation(e.operationUuid(),context),e.action(),e.subjectType().name(),e.subjectUuid(),null,Instant.now(clock),c));}
 private static void add(ArrayList<AuditChange> c,String field,String oldValue,String newValue,AuditValueType type){if(oldValue!=null||newValue!=null)c.add(new AuditChange(field,oldValue,newValue,type,c.size()));}
}
