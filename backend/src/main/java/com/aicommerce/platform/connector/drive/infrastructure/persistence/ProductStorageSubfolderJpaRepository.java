package com.aicommerce.platform.connector.drive.infrastructure.persistence;

import com.aicommerce.platform.connector.drive.domain.ProductStorageSubfolder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductStorageSubfolderJpaRepository extends JpaRepository<ProductStorageSubfolder,UUID> {
    List<ProductStorageSubfolder> findByStorageFolderUuidOrderByFolderRole(UUID storageFolderUuid);
}
