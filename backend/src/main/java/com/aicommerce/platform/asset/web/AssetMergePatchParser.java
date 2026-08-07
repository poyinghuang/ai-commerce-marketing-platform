package com.aicommerce.platform.asset.web;

import com.aicommerce.platform.asset.application.AssetValidationException;
import com.aicommerce.platform.asset.application.PatchAssetCommand;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AssetMergePatchParser {
    private static final Set<String> FIELDS=Set.of("assetType","purpose","storageProvider","providerFileId","fileUrl",
            "mediaType","originalFilename","sizeBytes","checksumSha256","providerMetadata");
    private final ObjectMapper mapper;
    public AssetMergePatchParser(ObjectMapper mapper) { this.mapper=mapper; }
    public PatchAssetCommand parse(JsonNode patch) {
        if(patch==null||!patch.isObject()) throw new InvalidMergePatchException("JSON Merge Patch must be an object");
        for(String field:patch.propertyNames()) if(!FIELDS.contains(field)) throw new InvalidMergePatchException("Field is not mutable: "+field);
        return new PatchAssetCommand(assetType(patch),string(patch,"purpose"),string(patch,"storageProvider"),
                string(patch,"providerFileId"),string(patch,"fileUrl"),string(patch,"mediaType"),
                string(patch,"originalFilename"),longValue(patch,"sizeBytes"),string(patch,"checksumSha256"),metadata(patch));
    }
    private FieldPatch<AssetType> assetType(JsonNode patch) {
        if(!patch.has("assetType")) return FieldPatch.absent();
        JsonNode value=patch.get("assetType");
        if(value.isNull()) throw new AssetValidationException("assetType","assetType is required");
        if(!value.isString()) throw new AssetValidationException("assetType","assetType must be a string");
        try { return FieldPatch.present(AssetType.valueOf(value.stringValue())); }
        catch(Exception e) { throw new AssetValidationException("assetType","assetType must be IMAGE, VIDEO, DOCUMENT, or OTHER"); }
    }
    private FieldPatch<String> string(JsonNode patch,String name) {
        if(!patch.has(name)) return FieldPatch.absent();
        JsonNode value=patch.get(name); if(value.isNull()) return FieldPatch.present(null);
        if(!value.isString()) throw new AssetValidationException(name,name+" must be a string or null");
        return FieldPatch.present(value.stringValue());
    }
    private FieldPatch<Long> longValue(JsonNode patch,String name) {
        if(!patch.has(name)) return FieldPatch.absent();
        JsonNode value=patch.get(name); if(value.isNull()) return FieldPatch.present(null);
        if(!value.isIntegralNumber() || !value.canConvertToLong()) throw new AssetValidationException(name,name+" must be an integer or null");
        return FieldPatch.present(value.longValue());
    }
    private FieldPatch<Map<String,Object>> metadata(JsonNode patch) {
        if(!patch.has("providerMetadata")) return FieldPatch.absent();
        return FieldPatch.present(parseProviderMetadata(patch.get("providerMetadata")));
    }
    public Map<String,Object> parseProviderMetadata(JsonNode value) {
        if(value==null || value.isNull()) return null;
        if(!value.isObject()) throw new AssetValidationException("providerMetadata","providerMetadata must be an object or null");
        return mapper.convertValue(value,new TypeReference<Map<String,Object>>(){});
    }
}
