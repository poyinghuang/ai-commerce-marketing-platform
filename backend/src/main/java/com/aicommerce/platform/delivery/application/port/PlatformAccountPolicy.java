package com.aicommerce.platform.delivery.application.port;
import java.util.*; import com.aicommerce.platform.delivery.domain.*;
public record PlatformAccountPolicy(UUID platformAccountUuid,ProviderKey providerKey,PlatformEnvironment environment,String currency,String timezone,boolean active){public PlatformAccountPolicy{Objects.requireNonNull(platformAccountUuid);Objects.requireNonNull(providerKey);Objects.requireNonNull(environment);if(!"TWD".equals(currency)||!"Asia/Taipei".equals(timezone))throw new IllegalArgumentException("PLATFORM_POLICY_REJECTED");}}
