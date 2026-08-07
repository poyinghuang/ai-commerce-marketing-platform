package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetQueryService {
    private final AssetJpaRepository assets; private final ProductJpaRepository products;
    public AssetQueryService(AssetJpaRepository assets, ProductJpaRepository products) { this.assets=assets; this.products=products; }
    @Transactional(readOnly=true)
    public Asset get(UUID productUuid,UUID assetUuid) {
        requireProduct(productUuid);
        return assets.findByAssetUuidAndProductUuid(assetUuid,productUuid).orElseThrow(AssetNotFoundException::new);
    }
    @Transactional(readOnly=true)
    public Page<Asset> list(UUID productUuid, LifecycleStatus status, AssetType assetType, UUID creativePlanUuid,
            UUID campaignUuid, String storageProvider, Pageable pageable) {
        requireProduct(productUuid);
        return assets.search(productUuid,status,assetType,creativePlanUuid,campaignUuid,storageProvider,pageable);
    }
    private void requireProduct(UUID uuid) { if(!products.existsById(uuid)) throw new ProductNotFoundException(uuid); }
}
