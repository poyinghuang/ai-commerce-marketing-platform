package com.aicommerce.platform.asset.application;

import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AssetAuditChangeFactory {
    private final AssetMetadataSecurity metadata;
    public AssetAuditChangeFactory(AssetMetadataSecurity metadata) { this.metadata = metadata; }
    public List<AuditChange> forCreate(AssetSnapshot after) { return differences(null, after); }
    public List<AuditChange> between(AssetSnapshot before, AssetSnapshot after) { return differences(before, after); }
    private List<AuditChange> differences(AssetSnapshot before, AssetSnapshot after) {
        List<AuditChange> result = new ArrayList<>();
        add(result,"creative_plan_uuid", before == null ? null : string(before.creativePlanUuid()), string(after.creativePlanUuid()), AuditValueType.UUID);
        add(result,"campaign_uuid", before == null ? null : string(before.campaignUuid()), string(after.campaignUuid()), AuditValueType.UUID);
        add(result,"asset_type", before == null ? null : string(before.assetType()), string(after.assetType()), AuditValueType.ENUM);
        add(result,"purpose", before == null ? null : before.purpose(), after.purpose(), AuditValueType.STRING);
        add(result,"storage_provider", before == null ? null : before.storageProvider(), after.storageProvider(), AuditValueType.STRING);
        add(result,"provider_file_id", before == null ? null : before.providerFileId(), after.providerFileId(), AuditValueType.STRING);
        add(result,"file_url", before == null ? null : before.fileUrl(), after.fileUrl(), AuditValueType.STRING);
        add(result,"media_type", before == null ? null : before.mediaType(), after.mediaType(), AuditValueType.STRING);
        add(result,"original_filename", before == null ? null : before.originalFilename(), after.originalFilename(), AuditValueType.STRING);
        add(result,"size_bytes", before == null ? null : string(before.sizeBytes()), string(after.sizeBytes()), AuditValueType.INTEGER);
        add(result,"checksum_sha256", before == null ? null : before.checksumSha256(), after.checksumSha256(), AuditValueType.STRING);
        add(result,"provider_metadata", before == null ? null : metadata.fingerprint(before.providerMetadata()), metadata.fingerprint(after.providerMetadata()), AuditValueType.STRING);
        add(result,"lifecycle_status", before == null ? null : string(before.lifecycleStatus()), string(after.lifecycleStatus()), AuditValueType.ENUM);
        add(result,"archived_at", before == null ? null : string(before.archivedAt()), string(after.archivedAt()), AuditValueType.TIMESTAMP);
        return List.copyOf(result);
    }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private void add(List<AuditChange> result, String field, String oldValue, String newValue, AuditValueType type) {
        if (!Objects.equals(oldValue,newValue)) result.add(new AuditChange(field,oldValue,newValue,type,result.size()));
    }
}
