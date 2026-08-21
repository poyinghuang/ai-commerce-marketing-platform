package com.aicommerce.platform.delivery.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class Stage4CViews {
    private Stage4CViews() {}

    public enum Warning {
        DETERMINISTIC_FAKE_ONLY,
        NO_REAL_PROVIDER_OR_SPEND,
        EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME
    }

    public record AdCreateRequest(
            UUID clientRequestUuid,
            UUID productUuid,
            UUID assetUuid,
            UUID generationOutputUuid,
            UUID reviewDecisionUuid) {}

    public record AdStateRequest(
            UUID clientRequestUuid,
            PlatformDesiredState targetDesiredState) {}

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record AdPreview(
            UUID clientRequestUuid,
            UUID platformAdSetUuid,
            long expectedParentVersion,
            UUID productUuid,
            UUID assetUuid,
            UUID generationOutputUuid,
            UUID reviewDecisionUuid,
            String approvedChecksumFingerprint,
            String creativeMappingKey,
            PlatformDesiredState parentCampaignDesiredState,
            PlatformDesiredState parentAdSetDesiredState,
            PlatformDesiredState newAdDesiredState,
            boolean evidenceEligible,
            List<Warning> warnings,
            boolean confirmable) {
        public AdPreview {
            Objects.requireNonNull(clientRequestUuid);
            Objects.requireNonNull(platformAdSetUuid);
            Objects.requireNonNull(productUuid);
            Objects.requireNonNull(assetUuid);
            Objects.requireNonNull(generationOutputUuid);
            Objects.requireNonNull(reviewDecisionUuid);
            Objects.requireNonNull(approvedChecksumFingerprint);
            Objects.requireNonNull(creativeMappingKey);
            Objects.requireNonNull(parentCampaignDesiredState);
            Objects.requireNonNull(parentAdSetDesiredState);
            Objects.requireNonNull(newAdDesiredState);
            warnings = List.copyOf(warnings);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record StatePreview(
            UUID clientRequestUuid,
            PlatformEntityType entityType,
            UUID entityUuid,
            long expectedEntityVersion,
            PlatformDesiredState previousDesiredState,
            PlatformDesiredState targetDesiredState,
            PlatformDesiredState parentCampaignDesiredState,
            PlatformDesiredState parentAdSetDesiredState,
            boolean evidenceEligible,
            List<Warning> warnings,
            boolean confirmable) {
        public StatePreview {
            Objects.requireNonNull(clientRequestUuid);
            Objects.requireNonNull(entityType);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(previousDesiredState);
            Objects.requireNonNull(targetDesiredState);
            Objects.requireNonNull(parentCampaignDesiredState);
            Objects.requireNonNull(parentAdSetDesiredState);
            warnings = List.copyOf(warnings);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Ad(
            UUID platformAdUuid,
            UUID platformAdSetUuid,
            UUID productUuid,
            UUID assetUuid,
            UUID generationOutputUuid,
            UUID reviewDecisionUuid,
            String approvedChecksumFingerprint,
            String creativeMappingKey,
            PlatformDesiredState desiredState,
            Optional<PlatformObservedState> observedState,
            Optional<String> externalIdFingerprint,
            Instant createdAt,
            Instant updatedAt,
            long version) {
        public Ad {
            Objects.requireNonNull(platformAdUuid);
            Objects.requireNonNull(platformAdSetUuid);
            Objects.requireNonNull(productUuid);
            Objects.requireNonNull(assetUuid);
            Objects.requireNonNull(generationOutputUuid);
            Objects.requireNonNull(reviewDecisionUuid);
            Objects.requireNonNull(approvedChecksumFingerprint);
            Objects.requireNonNull(creativeMappingKey);
            Objects.requireNonNull(desiredState);
            Objects.requireNonNull(observedState);
            Objects.requireNonNull(externalIdFingerprint);
            Objects.requireNonNull(createdAt);
            Objects.requireNonNull(updatedAt);
        }
    }
}
