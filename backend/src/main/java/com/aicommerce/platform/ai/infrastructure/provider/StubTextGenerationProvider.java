package com.aicommerce.platform.ai.infrastructure.provider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.aicommerce.platform.ai.application.TextGenerationProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
public class StubTextGenerationProvider implements TextGenerationProvider {

    @Override
    public TextResult generate(TextRequest request) {
        if ("stub-text-partial".equals(request.modelKey())
                && request.renderedPrompt().contains("\"variationIndex\":2")) {
            throw new com.aicommerce.platform.ai.application.AiProviderException(
                    "AI_PROVIDER_REJECTED", "Deterministic partial-failure fixture");
        }
        BigDecimal actualCost = "stub-text-cost-invariant".equals(request.modelKey())
                ? new BigDecimal("3.000000") : BigDecimal.ZERO;
        return new TextResult(
                "stub-text-" + request.generationJobUuid(),
                1,
                1,
                actualCost,
                "deterministic-local-stub",
                List.of(),
                Map.of("fixture", "stage-03"));
    }
}
