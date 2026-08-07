package com.aicommerce.platform.asset.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assets")
public class Asset extends ArchivableEntity {

    @Id
    @Column(name = "asset_uuid", nullable = false, updatable = false)
    private UUID assetUuid;
    @Column(name = "product_uuid", nullable = false, updatable = false)
    private UUID productUuid;
    @Column(name = "creative_plan_uuid", updatable = false)
    private UUID creativePlanUuid;
    @Column(name = "campaign_uuid", updatable = false)
    private UUID campaignUuid;
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private AssetType assetType;
    @Column(name = "purpose", length = 256)
    private String purpose;
    @Column(name = "storage_provider", length = 64)
    private String storageProvider;
    @Column(name = "provider_file_id", length = 512)
    private String providerFileId;
    @Column(name = "file_url", length = 2048)
    private String fileUrl;
    @Column(name = "media_type", length = 255)
    private String mediaType;
    @Column(name = "original_filename", length = 512)
    private String originalFilename;
    @Column(name = "size_bytes")
    private Long sizeBytes;
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_metadata", columnDefinition = "jsonb")
    private Map<String, Object> providerMetadata;

    protected Asset() {
    }

    private Asset(UUID assetUuid, UUID productUuid, UUID creativePlanUuid, UUID campaignUuid,
            AssetType assetType) {
        this.assetUuid = Objects.requireNonNull(assetUuid, "assetUuid is required");
        this.productUuid = Objects.requireNonNull(productUuid, "productUuid is required");
        this.creativePlanUuid = creativePlanUuid;
        this.campaignUuid = campaignUuid;
        this.assetType = Objects.requireNonNull(assetType, "assetType is required");
    }

    public static Asset create(UUID assetUuid, UUID productUuid, UUID creativePlanUuid, UUID campaignUuid,
            AssetType assetType) {
        return new Asset(assetUuid, productUuid, creativePlanUuid, campaignUuid, assetType);
    }

    public void update(AssetType assetType, String purpose, String storageProvider,
            String providerFileId, String fileUrl, String mediaType, String originalFilename,
            Long sizeBytes, String checksumSha256, Map<String, Object> providerMetadata) {
        if (getLifecycleStatus() == com.aicommerce.platform.common.domain.LifecycleStatus.ARCHIVED) {
            throw new IllegalStateException("Archived asset cannot be modified");
        }
        this.assetType = Objects.requireNonNull(assetType, "assetType is required");
        this.purpose = optional(purpose, "purpose", 256);
        this.storageProvider = optional(storageProvider, "storageProvider", 64);
        this.providerFileId = optional(providerFileId, "providerFileId", 512);
        this.fileUrl = url(fileUrl);
        this.mediaType = optional(mediaType, "mediaType", 255);
        this.originalFilename = optional(originalFilename, "originalFilename", 512);
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be non-negative");
        }
        this.sizeBytes = sizeBytes;
        String checksum = optional(checksumSha256, "checksumSha256", 64);
        if (checksum != null && !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksumSha256 must be 64 lower-case hexadecimal characters");
        }
        this.checksumSha256 = checksum;
        this.providerMetadata = providerMetadata == null ? null : new LinkedHashMap<>(providerMetadata);
    }

    private static String url(String value) {
        String normalized = optional(value, "fileUrl", 2048);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("fileUrl must be an absolute HTTP(S) URL");
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("fileUrl must be a valid URL", exception);
        }
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    public UUID getAssetUuid() { return assetUuid; }
    public UUID getProductUuid() { return productUuid; }
    public UUID getCreativePlanUuid() { return creativePlanUuid; }
    public UUID getCampaignUuid() { return campaignUuid; }
    public AssetType getAssetType() { return assetType; }
    public String getPurpose() { return purpose; }
    public String getStorageProvider() { return storageProvider; }
    public String getProviderFileId() { return providerFileId; }
    public String getFileUrl() { return fileUrl; }
    public String getMediaType() { return mediaType; }
    public String getOriginalFilename() { return originalFilename; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getChecksumSha256() { return checksumSha256; }
    public Map<String, Object> getProviderMetadata() { return providerMetadata; }
}
