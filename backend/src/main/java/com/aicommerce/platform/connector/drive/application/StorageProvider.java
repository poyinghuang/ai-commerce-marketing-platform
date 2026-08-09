package com.aicommerce.platform.connector.drive.application;

public interface StorageProvider {
    StorageFolderTree ensureProductTree(StorageEnsureRequest request);
}
