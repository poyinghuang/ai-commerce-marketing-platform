package com.aicommerce.platform.delivery.application.port;
import java.util.UUID;
public record PlatformCommandIdentity(UUID operationUuid,UUID platformAccountUuid,String idempotencyKey,String requestSha256){public PlatformCommandIdentity{PlatformContractSupport.req(operationUuid);PlatformContractSupport.req(platformAccountUuid);idempotencyKey=PlatformContractSupport.hash(idempotencyKey);requestSha256=PlatformContractSupport.hash(requestSha256);}}
