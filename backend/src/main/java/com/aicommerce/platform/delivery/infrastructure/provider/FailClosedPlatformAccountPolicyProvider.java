package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformAccountPolicyProvider;
import com.aicommerce.platform.delivery.application.port.PlatformAccountPolicy;
import com.aicommerce.platform.delivery.domain.ProviderKey;
import com.aicommerce.platform.delivery.domain.PlatformEnvironment;
import org.springframework.stereotype.Component;

@Component
public class FailClosedPlatformAccountPolicyProvider implements PlatformAccountPolicyProvider {
    @Override
    public PlatformAccountPolicy requirePolicy(UUID accountUuid) {
        return new PlatformAccountPolicy(accountUuid, ProviderKey.FAKE, PlatformEnvironment.TEST, "TWD", "Asia/Taipei", false);
    }
}
