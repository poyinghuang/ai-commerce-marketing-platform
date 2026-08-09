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
        return new TextResult(
                "stub-text-" + request.generationJobUuid(),
                1,
                1,
                BigDecimal.ZERO,
                "deterministic-local-stub",
                List.of(),
                Map.of("fixture", "stage-03"));
    }
}
