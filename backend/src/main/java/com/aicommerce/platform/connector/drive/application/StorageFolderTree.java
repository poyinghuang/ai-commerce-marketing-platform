package com.aicommerce.platform.connector.drive.application;

import com.aicommerce.platform.connector.drive.domain.StorageFolderRole;
import java.util.*;

public record StorageFolderTree(String rootFolderId, String sharedDriveId, String productFolderId,
        Map<StorageFolderRole,String> subfolderIds) {
    public StorageFolderTree {
        rootFolderId=require(rootFolderId); sharedDriveId=blank(sharedDriveId); productFolderId=require(productFolderId);
        subfolderIds=Map.copyOf(Objects.requireNonNull(subfolderIds));
        if(!subfolderIds.keySet().equals(EnumSet.allOf(StorageFolderRole.class)))
            throw new IllegalArgumentException("Storage tree must contain every folder role");
        subfolderIds.values().forEach(StorageFolderTree::require);
        Set<String> ids=new HashSet<>(subfolderIds.values());
        if(ids.size()!=StorageFolderRole.values().length||ids.contains(productFolderId))
            throw new IllegalArgumentException("Storage folder IDs must be unique");
    }
    private static String require(String value){if(value==null||value.isBlank()||value.length()>256)throw new IllegalArgumentException("folder ID is invalid");return value.trim();}
    private static String blank(String value){return value==null||value.isBlank()?null:require(value);}
}
