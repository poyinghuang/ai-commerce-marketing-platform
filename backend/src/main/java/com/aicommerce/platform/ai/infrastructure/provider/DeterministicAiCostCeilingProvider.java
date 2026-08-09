package com.aicommerce.platform.ai.infrastructure.provider;

import java.math.BigDecimal;
import java.util.Map;

import com.aicommerce.platform.ai.application.AiCostCeiling;
import com.aicommerce.platform.ai.application.AiCostCeilingProvider;
import com.aicommerce.platform.ai.domain.GenerationType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class DeterministicAiCostCeilingProvider implements AiCostCeilingProvider {

    private static final Map<String, AiCostCeiling> PROFILES = Map.of(
            "TEXT:stub:stub-text-low", ceiling("0.250000", "1.000000"),
            "TEXT:stub:stub-text", ceiling("0.500000", "2.000000"),
            "TEXT:stub:stub-text-partial", ceiling("0.500000", "2.000000"),
            "TEXT:stub:stub-text-cost-invariant", ceiling("0.500000", "2.000000"),
            "TEXT:stub:stub-text-daily", ceiling("0.500000", "6.000000"),
            "TEXT:stub:stub-text-over-job", ceiling("0.500000", "7.000000"),
            "IMAGE:stub:stub-image", ceiling("1.000000", "4.000000"));

    @Override
    public AiCostCeiling ceilingFor(GenerationType generationType, String providerKey, String modelKey) {
        AiCostCeiling ceiling = PROFILES.get(generationType + ":" + providerKey + ":" + modelKey);
        if (ceiling == null) {
            throw new IllegalArgumentException("AI provider/model cost profile is not allowlisted");
        }
        return ceiling;
    }

    private static AiCostCeiling ceiling(String estimated, String worstCase) {
        return new AiCostCeiling(new BigDecimal(estimated), new BigDecimal(worstCase));
    }
}
