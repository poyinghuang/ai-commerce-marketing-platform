package com.aicommerce.platform.ai.application;

import java.util.List;

import com.aicommerce.platform.ai.domain.GenerationBatch;
import com.aicommerce.platform.ai.domain.GenerationJob;

public record GenerationFoundationResult(
        GenerationBatch batch,
        List<GenerationJob> jobs,
        boolean budgetAccepted,
        String budgetRejectionCode) {
}
