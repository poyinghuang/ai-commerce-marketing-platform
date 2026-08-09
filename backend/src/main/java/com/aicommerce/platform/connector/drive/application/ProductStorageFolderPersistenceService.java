package com.aicommerce.platform.connector.drive.application;

import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.connector.drive.domain.*;
import com.aicommerce.platform.connector.drive.infrastructure.persistence.*;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.*;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStorageFolderPersistenceService {
    private final ProductStorageFolderJpaRepository folders; private final ProductStorageSubfolderJpaRepository subfolders;
    private final ProductJpaRepository products; private final AuditWriter audit; private final Clock clock;
    public ProductStorageFolderPersistenceService(ProductStorageFolderJpaRepository folders,ProductStorageSubfolderJpaRepository subfolders,
            ProductJpaRepository products,AuditWriter audit,Clock clock){this.folders=folders;this.subfolders=subfolders;this.products=products;this.audit=audit;this.clock=clock;}
    @Transactional
    public EnsureStorageFolderResult persist(UUID productUuid,StorageFolderTree tree,AuditOperationContext context){
        Optional<ProductStorageFolder> existing=folders.findByProductUuid(productUuid);
        if(existing.isPresent()) return new EnsureStorageFolderResult(view(existing.get()),false);
        Product product=products.findForAssetMutation(productUuid).orElseThrow(()->new ProductNotFoundException(productUuid));
        if(product.getLifecycleStatus()==ProductLifecycleStatus.ARCHIVED)throw new ProductArchivedException();
        ProductStorageFolder folder=folders.saveAndFlush(ProductStorageFolder.create(UUID.randomUUID(),productUuid,
                tree.rootFolderId(),tree.sharedDriveId(),tree.productFolderId()));
        List<ProductStorageSubfolder> children=new ArrayList<>();
        for(StorageFolderRole role:StorageFolderRole.values())children.add(ProductStorageSubfolder.create(UUID.randomUUID(),folder.getStorageFolderUuid(),role,tree.subfolderIds().get(role)));
        children=subfolders.saveAllAndFlush(children);
        List<AuditChange> changes=new ArrayList<>();
        add(changes,"storage_provider",null,"GOOGLE_DRIVE",AuditValueType.ENUM);
        add(changes,"root_folder_id",null,folder.getRootFolderId(),AuditValueType.STRING);
        add(changes,"shared_drive_id",null,folder.getSharedDriveId(),AuditValueType.STRING);
        add(changes,"product_folder_id",null,folder.getProductFolderId(),AuditValueType.STRING);
        for(ProductStorageSubfolder child:children)add(changes,"subfolder_"+child.getFolderRole().name().toLowerCase(Locale.ROOT),null,child.getProviderFolderId(),AuditValueType.STRING);
        audit.append(new AuditEvent(UUID.randomUUID(),context,AuditAction.CREATE,"PRODUCT_STORAGE_FOLDER",
                folder.getStorageFolderUuid(),productUuid,Instant.now(clock),changes));
        return new EnsureStorageFolderResult(ProductStorageFolderView.from(folder,children),true);
    }
    private ProductStorageFolderView view(ProductStorageFolder folder){return ProductStorageFolderView.from(folder,subfolders.findByStorageFolderUuidOrderByFolderRole(folder.getStorageFolderUuid()));}
    private void add(List<AuditChange> values,String field,String oldValue,String newValue,AuditValueType type){if(newValue!=null)values.add(new AuditChange(field,oldValue,newValue,type,values.size()));}
}
