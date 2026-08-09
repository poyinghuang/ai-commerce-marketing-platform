package com.aicommerce.platform.connector.drive.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name="product_storage_subfolders")
@EntityListeners(AuditingEntityListener.class)
public class ProductStorageSubfolder {
    @Id @Column(name="storage_subfolder_uuid",nullable=false,updatable=false) private UUID storageSubfolderUuid;
    @Column(name="storage_folder_uuid",nullable=false,updatable=false) private UUID storageFolderUuid;
    @Enumerated(EnumType.STRING) @Column(name="folder_role",nullable=false,updatable=false,length=32) private StorageFolderRole folderRole;
    @Column(name="provider_folder_id",nullable=false,updatable=false,length=256) private String providerFolderId;
    @CreatedDate @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected ProductStorageSubfolder(){}
    private ProductStorageSubfolder(UUID id,UUID parent,StorageFolderRole role,String providerId){
        storageSubfolderUuid=Objects.requireNonNull(id);storageFolderUuid=Objects.requireNonNull(parent);
        folderRole=Objects.requireNonNull(role);if(providerId==null||providerId.isBlank()||providerId.length()>256)throw new IllegalArgumentException("providerFolderId is invalid");providerFolderId=providerId.trim();
    }
    public static ProductStorageSubfolder create(UUID id,UUID parent,StorageFolderRole role,String providerId){return new ProductStorageSubfolder(id,parent,role,providerId);}
    public UUID getStorageSubfolderUuid(){return storageSubfolderUuid;} public UUID getStorageFolderUuid(){return storageFolderUuid;}
    public StorageFolderRole getFolderRole(){return folderRole;} public String getProviderFolderId(){return providerFolderId;}
    public Instant getCreatedAt(){return createdAt;}
}
