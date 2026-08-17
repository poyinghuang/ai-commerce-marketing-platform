package com.aicommerce.platform.delivery.application.port;
import java.util.*;
public record ReconciliationNotFound(Optional<String> safeProviderTraceId,NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome{public ReconciliationNotFound{safeProviderTraceId=PlatformContractSupport.opt(safeProviderTraceId);PlatformContractSupport.req(evidence);safeProviderTraceId.ifPresent(PlatformContractSupport::safe);}}
