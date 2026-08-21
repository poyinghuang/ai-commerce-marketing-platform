package com.aicommerce.platform.delivery.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.domain.AuditActor;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditSource;
import com.aicommerce.platform.delivery.domain.*;
import com.aicommerce.platform.delivery.infrastructure.persistence.PlatformOperationJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("(local | test) & !production")
public class Stage4CTransactions {
    private final Stage4BTransactions stage4b;
    private final Stage4CSupport support;
    private final PlatformOperationService operations;
    private final PlatformOperationJpaRepository operationRepository;
    private final PlatformOperationInputCanonicalizer canonicalizer;
    private final Stage4BUuidSource uuids;
    private final ObjectMapper mapper;

    public Stage4CTransactions(Stage4BTransactions stage4b, Stage4CSupport support, PlatformOperationService operations,
            PlatformOperationJpaRepository operationRepository, PlatformOperationInputCanonicalizer canonicalizer,
            Stage4BUuidSource uuids, ObjectMapper mapper) {
        this.stage4b = stage4b;
        this.support = support;
        this.operations = operations;
        this.operationRepository = operationRepository;
        this.canonicalizer = canonicalizer;
        this.uuids = uuids;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Stage4CViews.AdPreview previewCreate(UUID adSet, UUID request, UUID product, UUID asset, UUID output, UUID review, long parentVersion) {
        UUID account = stage4b.account();
        var chain = support.lockParents(account, adSet, false);
        if (chain.adSetVersion() != parentVersion) throw stale();
        support.requireParents(chain, PlatformDesiredState.PAUSED);
        String checksum = support.lookupChecksum(product, asset, output, review, false);
        return new Stage4CViews.AdPreview(request, adSet, chain.adSetVersion(), product, asset, output, review,
                Stage4CSupport.checksumFingerprint(checksum), Stage4CSupport.MAPPING, chain.campaignDesired(), chain.adSetDesired(),
                PlatformDesiredState.PAUSED, true, Stage4CSupport.WARNINGS, true);
    }

    @Transactional
    public Stage4BTransactions.Created confirmCreate(UUID adSet, UUID request, UUID product, UUID asset, UUID output, UUID review,
            long parentVersion, String requestId) {
        UUID account = stage4b.account();
        var existing = replay(account, request);
        if (existing.isPresent()) {
            return replayCreate(existing.get(), adSet, product, asset, output, review, parentVersion);
        }
        var chain = support.lockParents(account, adSet, true);
        if (chain.adSetVersion() != parentVersion) throw stale();
        support.requireParents(chain, PlatformDesiredState.PAUSED);
        String checksum = support.lookupChecksum(product, asset, output, review, true);
        UUID entity = uuids.request(request, "ad-entity");
        UUID operation = uuids.request(request, "operation");
        var payload = base(PlatformOperationType.CREATE_AD, PlatformEntityType.AD, entity);
        payload.put("platformAdUuid", entity);
        payload.put("platformAdSetUuid", adSet);
        payload.put("expectedParentVersion", parentVersion);
        payload.put("productUuid", product);
        payload.put("assetUuid", asset);
        payload.put("generationOutputUuid", output);
        payload.put("reviewDecisionUuid", review);
        payload.put("approvedChecksumSha256", checksum);
        payload.put("creativeMappingKey", Stage4CSupport.MAPPING);
        payload.put("desiredState", "PAUSED");
        String json;
        try {
            json = mapper.writeValueAsString(payload);
            canonicalizer.canonicalizeNewCreateAd(json);
        } catch (Exception exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID, Optional.of(operation));
        }
        PlatformOperation stored = operations.create(
                new CreatePlatformOperationCommand(operation, account, PlatformOperationType.CREATE_AD, PlatformEntityType.AD, entity, request, json, 3),
                new AuditOperationContext(operation, safeRequest(requestId), AuditActor.localAdmin(), AuditSource.API));
        return new Stage4BTransactions.Created(stored, false);
    }

    @Transactional(readOnly = true)
    public Stage4CViews.StatePreview previewState(UUID ad, UUID request, PlatformDesiredState target, long version) {
        UUID account = stage4b.account();
        var chain = support.lockAd(account, ad, false);
        if (chain.adVersion() != version) throw stale();
        boolean pause = target == PlatformDesiredState.PAUSED;
        if (pause) {
            if (chain.adDesired() != PlatformDesiredState.ACTIVE || chain.adExternal() == null) {
                throw new Stage4BException("PLATFORM_INVALID_OPERATION_STATE", HttpStatus.CONFLICT);
            }
        } else {
            if (chain.adDesired() != PlatformDesiredState.PAUSED || chain.adExternal() == null) {
                throw new Stage4BException("PLATFORM_INVALID_OPERATION_STATE", HttpStatus.CONFLICT);
            }
            support.requireParents(chain, PlatformDesiredState.ACTIVE);
            support.requireCurrentEvidence(chain, false);
        }
        return new Stage4CViews.StatePreview(request, PlatformEntityType.AD, ad, chain.adVersion(), chain.adDesired(), target,
                chain.campaignDesired(), chain.adSetDesired(), true, Stage4CSupport.WARNINGS, true);
    }

    @Transactional
    public Stage4BTransactions.Created confirmState(UUID ad, UUID request, PlatformDesiredState target, long version, String requestId) {
        UUID account = stage4b.account();
        PlatformOperationType type = target == PlatformDesiredState.PAUSED ? PlatformOperationType.PAUSE : PlatformOperationType.RESUME;
        var existing = replay(account, request);
        if (existing.isPresent()) return replayState(existing.get(), type, ad, version, target);
        var chain = support.lockAd(account, ad, true);
        if (chain.adVersion() != version) throw stale();
        if (target == PlatformDesiredState.PAUSED) {
            if (chain.adDesired() != PlatformDesiredState.ACTIVE || chain.adExternal() == null) {
                throw new Stage4BException("PLATFORM_INVALID_OPERATION_STATE", HttpStatus.CONFLICT);
            }
        } else {
            if (chain.adDesired() != PlatformDesiredState.PAUSED || chain.adExternal() == null) {
                throw new Stage4BException("PLATFORM_INVALID_OPERATION_STATE", HttpStatus.CONFLICT);
            }
            support.requireParents(chain, PlatformDesiredState.ACTIVE);
            support.requireCurrentEvidence(chain);
        }
        UUID operation = uuids.request(request, "operation");
        var payload = base(type, PlatformEntityType.AD, ad);
        payload.put("expectedEntityVersion", version);
        payload.put("targetDesiredState", target.name());
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID, Optional.of(operation));
        }
        PlatformOperation stored = operations.create(
                new CreatePlatformOperationCommand(operation, account, type, PlatformEntityType.AD, ad, request, json, 3),
                new AuditOperationContext(operation, safeRequest(requestId), AuditActor.localAdmin(), AuditSource.API));
        return new Stage4BTransactions.Created(stored, false);
    }

    @Transactional(readOnly = true)
    public Stage4CViews.Ad ad(UUID id) {
        UUID account = stage4b.account();
        var rows = support.readAd(account, id);
        return rows;
    }

    private Optional<PlatformOperation> replay(UUID account, UUID request) {
        return operationRepository.findByPlatformAccountUuidAndRequestedActorTypeAndRequestedActorIdAndClientRequestUuid(
                account, "LOCAL_ADMIN", "local-admin", request);
    }

    private Stage4BTransactions.Created replayCreate(PlatformOperation operation, UUID adSet, UUID product, UUID asset, UUID output, UUID review, long parentVersion) {
        try {
            var payload = mapper.readTree(operation.getRequestPayload());
            if (operation.getOperationType() != PlatformOperationType.CREATE_AD) throw conflict();
            if (!adSet.toString().equals(payload.path("platformAdSetUuid").asText())
                    || !product.toString().equals(payload.path("productUuid").asText())
                    || !asset.toString().equals(payload.path("assetUuid").asText())
                    || !output.toString().equals(payload.path("generationOutputUuid").asText())
                    || !review.toString().equals(payload.path("reviewDecisionUuid").asText())
                    || payload.path("expectedParentVersion").asLong() != parentVersion) {
                throw conflict();
            }
            return new Stage4BTransactions.Created(operation, true);
        } catch (Stage4BException | PlatformOperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw conflict();
        }
    }

    private Stage4BTransactions.Created replayState(PlatformOperation operation, PlatformOperationType type, UUID ad, long version, PlatformDesiredState target) {
        try {
            var payload = mapper.readTree(operation.getRequestPayload());
            if (operation.getOperationType() != type || !ad.equals(operation.getEntityUuid())
                    || payload.path("expectedEntityVersion").asLong() != version
                    || !target.name().equals(payload.path("targetDesiredState").asText())) {
                throw conflict();
            }
            return new Stage4BTransactions.Created(operation, true);
        } catch (Stage4BException | PlatformOperationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw conflict();
        }
    }

    private static LinkedHashMap<String, Object> base(PlatformOperationType op, PlatformEntityType type, UUID entity) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("schemaVersion", 1);
        payload.put("operationType", op.name());
        payload.put("entityType", type.name());
        payload.put("entityUuid", entity);
        return payload;
    }

    private static String safeRequest(String value) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1,128}") ? value : UUID.randomUUID().toString();
    }

    private static Stage4BException stale() {
        return new Stage4BException("PLATFORM_ENTITY_STALE", HttpStatus.PRECONDITION_FAILED);
    }

    private static Stage4BException conflict() {
        return new Stage4BException("PLATFORM_IDEMPOTENCY_CONFLICT", HttpStatus.CONFLICT);
    }
}
