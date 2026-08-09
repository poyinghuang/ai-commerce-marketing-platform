package com.aicommerce.platform.ai.application;

import com.aicommerce.platform.ai.domain.GenerationType;

public interface AiCostCeilingProvider {

    AiCostCeiling ceilingFor(GenerationType generationType, String providerKey, String modelKey);
}
