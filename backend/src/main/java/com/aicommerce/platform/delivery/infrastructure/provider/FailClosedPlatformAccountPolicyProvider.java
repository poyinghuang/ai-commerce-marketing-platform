package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformAccountPolicyProvider;
import org.springframework.stereotype.Component;

@Component
public class FailClosedPlatformAccountPolicyProvider implements PlatformAccountPolicyProvider {
    @Override
    public AccountPolicy resolve(UUID accountUuid) {
        return new AccountPolicy(accountUuid, "TWD", "Asia/Taipei", false);
    }
}
