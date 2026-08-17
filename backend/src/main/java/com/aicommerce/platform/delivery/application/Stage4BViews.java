package com.aicommerce.platform.delivery.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.*;
import com.fasterxml.jackson.annotation.JsonInclude;

public final class Stage4BViews {
    private Stage4BViews() {}

    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Policy(String currency, String businessZone, String objective, String optimizationGoal,
            String targetingProfile, String placementProfile, Optional<String> maxEntityAmount,
            String maxBatchAmount, String maxAccountDayAmount) { public Policy { Objects.requireNonNull(currency);Objects.requireNonNull(businessZone);Objects.requireNonNull(objective);Objects.requireNonNull(optimizationGoal);Objects.requireNonNull(targetingProfile);Objects.requireNonNull(placementProfile);Objects.requireNonNull(maxEntityAmount);Objects.requireNonNull(maxBatchAmount);Objects.requireNonNull(maxAccountDayAmount); } }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Reservation(String kind, Optional<String> previousAmount, Optional<String> newAmount,
            String reservedDelta, LocalDate businessDate, String batchReservedAfter,
            String accountDayReservedBefore, String accountDayReservedAfter, String accountDayRemainingAfter) { public Reservation { Objects.requireNonNull(kind);Objects.requireNonNull(previousAmount);Objects.requireNonNull(newAmount);Objects.requireNonNull(reservedDelta);Objects.requireNonNull(businessDate);Objects.requireNonNull(batchReservedAfter);Objects.requireNonNull(accountDayReservedBefore);Objects.requireNonNull(accountDayReservedAfter);Objects.requireNonNull(accountDayRemainingAfter); } }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Preview(UUID clientRequestUuid, PlatformOperationType operationType, PlatformEntityType entityType,
            Optional<UUID> entityUuid, Optional<Long> expectedEntityVersion,
            Optional<Long> expectedCampaignPlanVersion, PlatformDesiredState desiredState,
            Optional<PlatformBudgetType> budgetType, Optional<String> budgetAmount,
            Optional<Instant> scheduleStart, Optional<Instant> scheduleEnd, Policy policy,
            Reservation reservation, List<String> warnings, boolean confirmable) { public Preview { Objects.requireNonNull(clientRequestUuid);Objects.requireNonNull(operationType);Objects.requireNonNull(entityType);Objects.requireNonNull(entityUuid);Objects.requireNonNull(expectedEntityVersion);Objects.requireNonNull(expectedCampaignPlanVersion);Objects.requireNonNull(desiredState);Objects.requireNonNull(budgetType);Objects.requireNonNull(budgetAmount);Objects.requireNonNull(scheduleStart);Objects.requireNonNull(scheduleEnd);Objects.requireNonNull(policy);Objects.requireNonNull(reservation);warnings=List.copyOf(warnings); } }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Campaign(UUID platformCampaignUuid, UUID campaignUuid, long campaignPlanVersion,
            String objective, String accountTimezone, PlatformDesiredState desiredState,
            Optional<PlatformObservedState> observedState, Optional<Instant> scheduleStart,
            Optional<Instant> scheduleEnd, Optional<String> externalIdFingerprint, long version) { public Campaign { Objects.requireNonNull(platformCampaignUuid);Objects.requireNonNull(campaignUuid);Objects.requireNonNull(objective);Objects.requireNonNull(accountTimezone);Objects.requireNonNull(desiredState);Objects.requireNonNull(observedState);Objects.requireNonNull(scheduleStart);Objects.requireNonNull(scheduleEnd);Objects.requireNonNull(externalIdFingerprint); } }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record AdSet(UUID platformAdSetUuid, UUID platformCampaignUuid,
            PlatformDesiredState desiredState, Optional<PlatformObservedState> observedState,
            PlatformBudgetType budgetType, String budgetAmount, String currency, String accountTimezone,
            String optimizationGoal, String targetingProfile, String placementProfile,
            Optional<Instant> scheduleStart, Optional<Instant> scheduleEnd,
            Optional<String> externalIdFingerprint, long version) { public AdSet { Objects.requireNonNull(platformAdSetUuid);Objects.requireNonNull(platformCampaignUuid);Objects.requireNonNull(desiredState);Objects.requireNonNull(observedState);Objects.requireNonNull(budgetType);Objects.requireNonNull(budgetAmount);Objects.requireNonNull(currency);Objects.requireNonNull(accountTimezone);Objects.requireNonNull(optimizationGoal);Objects.requireNonNull(targetingProfile);Objects.requireNonNull(placementProfile);Objects.requireNonNull(scheduleStart);Objects.requireNonNull(scheduleEnd);Objects.requireNonNull(externalIdFingerprint); } }
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public record Operation(UUID operationUuid, PlatformOperationType operationType, PlatformEntityType entityType,
            UUID entityUuid, PlatformOperationStatus status, int attemptCount, int reconciliationCount,
            int maxAttempts, Optional<PlatformStableErrorCode> normalizedErrorCode,
            Optional<Instant> nextAttemptAt, Optional<Instant> completedAt,
            Instant createdAt, Instant updatedAt, long version) { public Operation { Objects.requireNonNull(operationUuid);Objects.requireNonNull(operationType);Objects.requireNonNull(entityType);Objects.requireNonNull(entityUuid);Objects.requireNonNull(status);Objects.requireNonNull(normalizedErrorCode);Objects.requireNonNull(nextAttemptAt);Objects.requireNonNull(completedAt);Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt); } }
    public record Confirmation(Operation operation, boolean replay) { public Confirmation { Objects.requireNonNull(operation); } }
}
