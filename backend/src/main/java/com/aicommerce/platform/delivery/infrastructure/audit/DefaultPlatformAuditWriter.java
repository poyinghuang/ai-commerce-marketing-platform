package com.aicommerce.platform.delivery.infrastructure.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.delivery.application.audit.*;
import org.springframework.stereotype.Component;

@Component
public final class DefaultPlatformAuditWriter implements PlatformAuditWriter {
 private final AuditWriter writer;private final AuditOperationContextFactory contexts;private final Clock clock;
 public DefaultPlatformAuditWriter(AuditWriter writer,AuditOperationContextFactory contexts,Clock clock){this.writer=writer;this.contexts=contexts;this.clock=clock;}
 @Override public void write(PlatformAuditEvent e,AuditOperationContext context){var c=new ArrayList<AuditChange>();add(c,"operationStatus",e.previousOperationStatus().map(Enum::name).orElse(null),e.newOperationStatus().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"attemptKind",null,e.attemptKind().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"attemptNumber",null,e.attemptNumber().map(String::valueOf).orElse(null),AuditValueType.INTEGER);add(c,"attemptStatus",e.previousAttemptStatus().map(Enum::name).orElse(null),e.newAttemptStatus().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"desiredState",e.previousDesiredState().map(Enum::name).orElse(null),e.newDesiredState().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"observedState",e.previousObservedState().map(Enum::name).orElse(null),e.newObservedState().map(Enum::name).orElse(null),AuditValueType.ENUM);add(c,"budgetAmount",e.previousBudgetAmount().map(v->v.stripTrailingZeros().toPlainString()).orElse(null),e.newBudgetAmount().map(v->v.stripTrailingZeros().toPlainString()).orElse(null),AuditValueType.DECIMAL);add(c,"externalIdFingerprint",null,e.externalIdFingerprint().orElse(null),AuditValueType.STRING);add(c,"normalizedErrorCode",null,e.normalizedErrorCode().map(Enum::name).orElse(null),AuditValueType.STRING);add(c,"safeProviderTraceId",null,e.safeProviderTraceId().orElse(null),AuditValueType.STRING);writer.append(new AuditEvent(UUID.randomUUID(),contexts.forStableOperation(e.operationUuid(),context),e.action(),e.subjectType().name(),e.subjectUuid(),null,Instant.now(clock),c));}
 private static void add(ArrayList<AuditChange> c,String field,String oldValue,String newValue,AuditValueType type){if(oldValue!=null||newValue!=null)c.add(new AuditChange(field,oldValue,newValue,type,c.size()));}
}
