package com.aicommerce.platform.delivery.infrastructure.provider;

import java.util.UUID;

import com.aicommerce.platform.delivery.application.port.PlatformCredentialProvider;
import org.springframework.stereotype.Component;

@Component
public class FailClosedPlatformCredentialProvider implements PlatformCredentialProvider {
    @Override
    public CredentialHandle resolve(UUID accountUuid) {
        throw new IllegalStateException("Platform credentials are unavailable in Stage 4A");
    }
}
