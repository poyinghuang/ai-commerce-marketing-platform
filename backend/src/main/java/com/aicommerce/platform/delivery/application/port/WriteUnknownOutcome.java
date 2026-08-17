package com.aicommerce.platform.delivery.application.port;
import java.util.*; import com.aicommerce.platform.delivery.domain.PlatformUnknownCode;
public record WriteUnknownOutcome(PlatformUnknownCode errorCode,Optional<String> safeProviderTraceId,NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome{public WriteUnknownOutcome{PlatformContractSupport.req(errorCode);safeProviderTraceId=PlatformContractSupport.opt(safeProviderTraceId);PlatformContractSupport.req(evidence);safeProviderTraceId.ifPresent(PlatformContractSupport::safe);}}
