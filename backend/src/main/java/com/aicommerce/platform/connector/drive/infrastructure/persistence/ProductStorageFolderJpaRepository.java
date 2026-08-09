package com.aicommerce.platform.connector.drive.infrastructure.persistence;

import com.aicommerce.platform.connector.drive.domain.ProductStorageFolder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStorageFolderJpaRepository extends JpaRepository<ProductStorageFolder,UUID> {
    Optional<ProductStorageFolder> findByProductUuid(UUID productUuid);
}
