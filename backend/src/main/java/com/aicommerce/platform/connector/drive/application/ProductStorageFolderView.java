package com.aicommerce.platform.connector.drive.application;

import com.aicommerce.platform.connector.drive.domain.*;
import java.time.Instant;
import java.util.*;

public record ProductStorageFolderView(UUID storageFolderUuid,UUID productUuid,String storageProvider,
        String rootFolderId,String sharedDriveId,String productFolderId,Map<String,String> subfolders,
        Instant createdAt,Instant updatedAt,long version) {
    public static ProductStorageFolderView from(ProductStorageFolder folder,List<ProductStorageSubfolder> children){
        Map<String,String> values=new LinkedHashMap<>();
        children.stream().sorted(Comparator.comparing(v->v.getFolderRole().ordinal()))
                .forEach(v->values.put(v.getFolderRole().name(),v.getProviderFolderId()));
        return new ProductStorageFolderView(folder.getStorageFolderUuid(),folder.getProductUuid(),folder.getStorageProvider(),
                folder.getRootFolderId(),folder.getSharedDriveId(),folder.getProductFolderId(),Map.copyOf(values),
                folder.getCreatedAt(),folder.getUpdatedAt(),folder.getVersion());
    }
}
