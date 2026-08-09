package com.aicommerce.platform.ai.web;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTextGenerationBatchRequest(
        @Pattern(regexp = "TEXT|IMAGE") String generationType,
        @NotNull UUID creativePlanUuid,
        @NotBlank @Size(max = 128) @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,127}") String templateKey,
        @NotBlank @Pattern(regexp = "STANDARD|LOW_COST|PARTIAL_FAILURE_FIXTURE|COST_INVARIANT_FIXTURE|STANDARD_IMAGE") String modelProfile,
        @Min(1) @Max(3) Integer variationCount,
        @Size(max = 128) @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,127}") String workflowKey,
        UUID sourceAssetUuid,
        UUID maskAssetUuid) {
}
