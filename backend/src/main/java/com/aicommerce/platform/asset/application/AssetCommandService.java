package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.campaign.domain.CampaignPlan;
import com.aicommerce.platform.campaign.domain.CampaignProduct;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignPlanJpaRepository;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignProductJpaRepository;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.domain.CreativePlan;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.domain.ProductLifecycleStatus;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetCommandService {
    private static final String ENTITY_TYPE = "ASSET";
    private final AssetJpaRepository assets;
    private final ProductJpaRepository products;
    private final CreativePlanJpaRepository creativePlans;
    private final CampaignPlanJpaRepository campaigns;
    private final CampaignProductJpaRepository campaignProducts;
    private final AuditOperationContextFactory contexts;
    private final AuditWriter auditWriter;
    private final AssetAuditChangeFactory changes;
    private final AssetMetadataSecurity metadata;
    private final ProductQualityRecalculationService quality;
    private final Clock clock;

    public AssetCommandService(AssetJpaRepository assets, ProductJpaRepository products,
            CreativePlanJpaRepository creativePlans, CampaignPlanJpaRepository campaigns,
            CampaignProductJpaRepository campaignProducts, AuditOperationContextFactory contexts,
            AuditWriter auditWriter, AssetAuditChangeFactory changes, AssetMetadataSecurity metadata,
            ProductQualityRecalculationService quality, Clock clock) {
        this.assets=assets; this.products=products; this.creativePlans=creativePlans; this.campaigns=campaigns;
        this.campaignProducts=campaignProducts; this.contexts=contexts; this.auditWriter=auditWriter;
        this.changes=changes; this.metadata=metadata; this.quality=quality; this.clock=clock;
    }

    @Transactional
    public Asset create(UUID productUuid, CreateAssetCommand command, String requestId) {
        AuditOperationContext context=context(requestId);
        requireActiveProduct(productUuid);
        validateReferences(productUuid, command.creativePlanUuid(), command.campaignUuid());
        Asset asset;
        try {
            asset=Asset.create(UUID.randomUUID(),productUuid,command.creativePlanUuid(),command.campaignUuid(),command.assetType());
            asset.update(command.assetType(),command.purpose(),command.storageProvider(),command.providerFileId(),
                    command.fileUrl(),command.mediaType(),command.originalFilename(),command.sizeBytes(),
                    command.checksumSha256(),metadata.validateAndCanonicalize(command.providerMetadata()));
        } catch (IllegalArgumentException | NullPointerException exception) { throw validation(exception); }
        asset=assets.saveAndFlush(asset);
        append(asset,context,AuditAction.CREATE,changes.forCreate(AssetSnapshot.from(asset)));
        quality.recalculate(productUuid, context);
        return asset;
    }

    @Transactional
    public Asset patch(UUID productUuid, UUID assetUuid, long expectedVersion, PatchAssetCommand command, String requestId) {
        AuditOperationContext context=context(requestId);
        requireActiveProduct(productUuid);
        Asset asset=findForMutation(productUuid,assetUuid); checkVersion(asset,expectedVersion);
        if (asset.getLifecycleStatus()==LifecycleStatus.ARCHIVED) throw new AssetArchivedException();
        validateReferences(productUuid,asset.getCreativePlanUuid(),asset.getCampaignUuid());
        AssetSnapshot before=AssetSnapshot.from(asset);
        try {
            asset.update(command.assetType().resolve(asset.getAssetType()),command.purpose().resolve(asset.getPurpose()),
                    command.storageProvider().resolve(asset.getStorageProvider()),command.providerFileId().resolve(asset.getProviderFileId()),
                    command.fileUrl().resolve(asset.getFileUrl()),command.mediaType().resolve(asset.getMediaType()),
                    command.originalFilename().resolve(asset.getOriginalFilename()),command.sizeBytes().resolve(asset.getSizeBytes()),
                    command.checksumSha256().resolve(asset.getChecksumSha256()),
                    command.providerMetadata().present() ? metadata.validateAndCanonicalize(command.providerMetadata().value()) : asset.getProviderMetadata());
        } catch (IllegalArgumentException | NullPointerException exception) { throw validation(exception); }
        List<AuditChange> actual=changes.between(before,AssetSnapshot.from(asset));
        if (actual.isEmpty()) return asset;
        flush(asset); append(asset,context,AuditAction.UPDATE,actual); quality.recalculate(productUuid, context); return asset;
    }

    @Transactional
    public Asset archive(UUID productUuid, UUID assetUuid, long expectedVersion, String requestId) {
        AuditOperationContext context=context(requestId); requireActiveProduct(productUuid);
        Asset asset=findForMutation(productUuid,assetUuid); checkVersion(asset,expectedVersion);
        AssetSnapshot before=AssetSnapshot.from(asset);
        if (!asset.archive(Instant.now(clock))) return asset;
        List<AuditChange> actual=changes.between(before,AssetSnapshot.from(asset));
        flush(asset); append(asset,context,AuditAction.ARCHIVE,actual); quality.recalculate(productUuid, context); return asset;
    }

    @Transactional
    public Asset restore(UUID productUuid, UUID assetUuid, long expectedVersion, String requestId) {
        AuditOperationContext context=context(requestId); requireActiveProduct(productUuid);
        Asset asset=findForMutation(productUuid,assetUuid); checkVersion(asset,expectedVersion);
        validateReferences(productUuid,asset.getCreativePlanUuid(),asset.getCampaignUuid());
        AssetSnapshot before=AssetSnapshot.from(asset);
        if (!asset.restore()) return asset;
        List<AuditChange> actual=changes.between(before,AssetSnapshot.from(asset));
        flush(asset); append(asset,context,AuditAction.RESTORE,actual); quality.recalculate(productUuid, context); return asset;
    }

    private Product requireActiveProduct(UUID id) {
        Product product=products.findForAssetMutation(id).orElseThrow(() -> new ProductNotFoundException(id));
        if (product.getLifecycleStatus()==ProductLifecycleStatus.ARCHIVED) throw new ProductArchivedException();
        return product;
    }
    private void validateReferences(UUID productUuid, UUID creativePlanUuid, UUID campaignUuid) {
        if (creativePlanUuid != null) {
            CreativePlan plan=creativePlans.findForAssetMutation(creativePlanUuid,productUuid)
                    .orElseThrow(AssetRelationshipConflictException::new);
            if (plan.getLifecycleStatus()!=LifecycleStatus.ACTIVE) throw new AssetRelationshipConflictException();
        }
        if (campaignUuid != null) {
            CampaignPlan campaign=campaigns.findForMutation(campaignUuid).orElseThrow(AssetRelationshipConflictException::new);
            CampaignProduct association=campaignProducts.findForMutation(campaignUuid,productUuid)
                    .orElseThrow(AssetRelationshipConflictException::new);
            if (campaign.getLifecycleStatus()!=LifecycleStatus.ACTIVE || association.getLifecycleStatus()!=LifecycleStatus.ACTIVE)
                throw new AssetRelationshipConflictException();
        }
    }
    private Asset findForMutation(UUID productUuid, UUID assetUuid) {
        return assets.findForMutation(assetUuid,productUuid).orElseThrow(AssetNotFoundException::new);
    }
    private void checkVersion(Asset asset,long expected) { if(asset.getVersion()!=expected) throw new AssetPreconditionFailedException(); }
    private void flush(Asset asset) {
        try { assets.saveAndFlush(asset); } catch (ObjectOptimisticLockingFailureException e) { throw new AssetPreconditionFailedException(); }
    }
    private AuditOperationContext context(String requestId) {
        try { return contexts.forCurrentActor(requestId); } catch (IllegalStateException e) { throw new AuditActorUnavailableException(e); }
    }
    private void append(Asset asset, AuditOperationContext context, AuditAction action, List<AuditChange> actual) {
        if(!actual.isEmpty()) auditWriter.append(new AuditEvent(UUID.randomUUID(),context,action,ENTITY_TYPE,
                asset.getAssetUuid(),asset.getProductUuid(),Instant.now(clock),actual));
    }
    private AssetValidationException validation(RuntimeException exception) {
        if (exception instanceof AssetValidationException ave) return ave;
        String message=exception.getMessage()==null?"Asset validation failed":exception.getMessage();
        String field=message.contains(" ")?message.substring(0,message.indexOf(' ')):"asset";
        return new AssetValidationException(field,message);
    }
}
