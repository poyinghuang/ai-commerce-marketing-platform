package com.aicommerce.platform.delivery.application.port;
import java.util.*; import com.aicommerce.platform.delivery.domain.PlatformWriteTerminalCode;
public record WriteTerminalFailure(PlatformWriteTerminalCode errorCode,Optional<String> safeProviderTraceId,NormalizedPlatformEvidence evidence) implements PlatformWriteOutcome{public WriteTerminalFailure{PlatformContractSupport.req(errorCode);safeProviderTraceId=PlatformContractSupport.opt(safeProviderTraceId);PlatformContractSupport.req(evidence);safeProviderTraceId.ifPresent(PlatformContractSupport::safe);}}
