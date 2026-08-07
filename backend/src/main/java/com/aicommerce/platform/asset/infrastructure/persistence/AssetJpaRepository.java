package com.aicommerce.platform.asset.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.asset.domain.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetJpaRepository extends JpaRepository<Asset, UUID> {
}
