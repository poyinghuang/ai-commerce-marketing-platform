package com.aicommerce.platform.connector.drive.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_storage_folders")
@EntityListeners(AuditingEntityListener.class)
public class ProductStorageFolder {
    @Id @Column(name="storage_folder_uuid", nullable=false, updatable=false) private UUID storageFolderUuid;
    @Column(name="product_uuid", nullable=false, updatable=false) private UUID productUuid;
    @Column(name="storage_provider", nullable=false, updatable=false, length=32) private String storageProvider;
    @Column(name="root_folder_id", nullable=false, updatable=false, length=256) private String rootFolderId;
    @Column(name="shared_drive_id", updatable=false, length=256) private String sharedDriveId;
    @Column(name="product_folder_id", nullable=false, updatable=false, length=256) private String productFolderId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="provider_metadata", nullable=false, columnDefinition="jsonb") private String providerMetadata;
    @CreatedDate @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @LastModifiedDate @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;

    protected ProductStorageFolder() {}
    private ProductStorageFolder(UUID id, UUID productUuid, String rootId, String sharedId, String productId) {
        this.storageFolderUuid=Objects.requireNonNull(id); this.productUuid=Objects.requireNonNull(productUuid);
        this.storageProvider="GOOGLE_DRIVE"; this.rootFolderId=require(rootId); this.sharedDriveId=blank(sharedId);
        this.productFolderId=require(productId); this.providerMetadata="{}";
    }
    public static ProductStorageFolder create(UUID id, UUID productUuid, String rootId, String sharedId, String productId) {
        return new ProductStorageFolder(id,productUuid,rootId,sharedId,productId);
    }
    private static String require(String value){if(value==null||value.isBlank()||value.length()>256)throw new IllegalArgumentException("folderId is invalid");return value.trim();}
    private static String blank(String value){return value==null||value.isBlank()?null:require(value);}
    public UUID getStorageFolderUuid(){return storageFolderUuid;} public UUID getProductUuid(){return productUuid;}
    public String getStorageProvider(){return storageProvider;} public String getRootFolderId(){return rootFolderId;}
    public String getSharedDriveId(){return sharedDriveId;} public String getProductFolderId(){return productFolderId;}
    public String getProviderMetadata(){return providerMetadata;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
