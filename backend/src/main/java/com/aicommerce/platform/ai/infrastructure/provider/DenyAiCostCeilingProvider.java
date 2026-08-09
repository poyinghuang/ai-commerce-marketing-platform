package com.aicommerce.platform.ai.infrastructure.provider;

import com.aicommerce.platform.ai.application.AiCostCeiling;
import com.aicommerce.platform.ai.application.AiCostCeilingProvider;
import com.aicommerce.platform.ai.application.AiProviderException;
import com.aicommerce.platform.ai.domain.GenerationType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production | (!local & !test)")
public class DenyAiCostCeilingProvider implements AiCostCeilingProvider {

    @Override
    public AiCostCeiling ceilingFor(GenerationType generationType, String providerKey, String modelKey) {
        throw new AiProviderException(
                "AI_PROVIDER_NOT_CONFIGURED",
                "A trusted AI cost ceiling provider is not configured");
    }
}
