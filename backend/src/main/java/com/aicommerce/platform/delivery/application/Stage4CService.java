package com.aicommerce.platform.delivery.application;

import java.util.UUID;

import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformOperation;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("(local | test) & !production")
public class Stage4CService {
    private final Stage4CTransactions tx;
    private final PlatformOperationService operationService;

    public Stage4CService(Stage4CTransactions tx, PlatformOperationService operationService) {
        this.tx = tx;
        this.operationService = operationService;
    }

    public Stage4CViews.AdPreview previewCreate(UUID adSet, UUID request, UUID product, UUID asset, UUID output, UUID review, long parentVersion) {
        return tx.previewCreate(adSet, request, product, asset, output, review, parentVersion);
    }

    public Stage4BViews.Confirmation confirmCreate(UUID adSet, UUID request, UUID product, UUID asset, UUID output, UUID review,
            long parentVersion, String requestId) {
        return dispatch(tx.confirmCreate(adSet, request, product, asset, output, review, parentVersion, requestId));
    }

    public Stage4CViews.StatePreview previewState(UUID ad, UUID request, PlatformDesiredState target, long version) {
        return tx.previewState(ad, request, target, version);
    }

    public Stage4BViews.Confirmation confirmState(UUID ad, UUID request, PlatformDesiredState target, long version, String requestId) {
        return dispatch(tx.confirmState(ad, request, target, version, requestId));
    }

    public Stage4CViews.Ad ad(UUID id) {
        return tx.ad(id);
    }

    private Stage4BViews.Confirmation dispatch(Stage4BTransactions.Created created) {
        if (created.replay()) return new Stage4BViews.Confirmation(view(created.operation()), true);
        return new Stage4BViews.Confirmation(from(operationService.submit(created.operation().getOperationUuid(), created.operation().getVersion(), null)), false);
    }

    private static Stage4BViews.Operation from(PlatformOperationView o) {
        return new Stage4BViews.Operation(o.operationUuid(), o.operationType(), o.entityType(), o.entityUuid(), o.status(),
                o.attemptCount(), o.reconciliationCount(), o.maxAttempts(), o.normalizedErrorCode(), o.nextAttemptAt(),
                o.completedAt(), o.createdAt(), o.updatedAt(), o.version());
    }

    private static Stage4BViews.Operation view(PlatformOperation o) {
        return new Stage4BViews.Operation(o.getOperationUuid(), o.getOperationType(), o.getEntityType(), o.getEntityUuid(),
                o.getStatus(), o.getAttemptCount(), o.getReconciliationCount(), o.getMaxAttempts(),
                java.util.Optional.ofNullable(o.getNormalizedErrorCode()).map(PlatformStableErrorCode::valueOf),
                java.util.Optional.ofNullable(o.getNextAttemptAt()), java.util.Optional.ofNullable(o.getCompletedAt()),
                o.getCreatedAt(), o.getUpdatedAt(), o.getVersion());
    }
}
