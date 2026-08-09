package com.aicommerce.platform.connector.drive.application;

import com.aicommerce.platform.connector.drive.infrastructure.persistence.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStorageFolderQueryService {
    private final ProductStorageFolderJpaRepository folders; private final ProductStorageSubfolderJpaRepository subfolders;
    public ProductStorageFolderQueryService(ProductStorageFolderJpaRepository folders,ProductStorageSubfolderJpaRepository subfolders){this.folders=folders;this.subfolders=subfolders;}
    @Transactional(readOnly=true)
    public ProductStorageFolderView get(UUID productUuid){return folders.findByProductUuid(productUuid).map(this::view).orElseThrow(StorageFolderNotFoundException::new);}
    @Transactional(readOnly=true)
    public Optional<ProductStorageFolderView> find(UUID productUuid){return folders.findByProductUuid(productUuid).map(this::view);}
    private ProductStorageFolderView view(com.aicommerce.platform.connector.drive.domain.ProductStorageFolder folder){return ProductStorageFolderView.from(folder,subfolders.findByStorageFolderUuidOrderByFolderRole(folder.getStorageFolderUuid()));}
}
