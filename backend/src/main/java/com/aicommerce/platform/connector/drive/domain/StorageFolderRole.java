package com.aicommerce.platform.connector.drive.domain;

public enum StorageFolderRole {
    ORIGINAL("original"), IMAGES("images"), VIDEOS("videos"), DOCUMENTS("documents"),
    CAMPAIGNS("campaigns"), ARCHIVE("archive");

    private final String folderName;
    StorageFolderRole(String folderName) { this.folderName = folderName; }
    public String folderName() { return folderName; }
}
