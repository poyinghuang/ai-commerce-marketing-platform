package com.aicommerce.platform.delivery.application.port;
import java.util.*;
public record ReconciliationStillUnknown(Optional<String> safeProviderTraceId,NormalizedPlatformEvidence evidence) implements PlatformReconciliationOutcome{public ReconciliationStillUnknown{safeProviderTraceId=PlatformContractSupport.opt(safeProviderTraceId);PlatformContractSupport.req(evidence);safeProviderTraceId.ifPresent(PlatformContractSupport::safe);}}
