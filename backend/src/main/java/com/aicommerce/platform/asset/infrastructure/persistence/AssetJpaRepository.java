package com.aicommerce.platform.asset.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.asset.domain.Asset;
import com.aicommerce.platform.common.persistence.ArchivableResourceRepository;

public interface AssetJpaRepository extends ArchivableResourceRepository<Asset, UUID> {
}
