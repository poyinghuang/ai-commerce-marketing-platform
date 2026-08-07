package com.aicommerce.platform.asset.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AssetMetadataSecurity {
    private static final int MAX_BYTES = 16 * 1024;
    private static final List<String> FORBIDDEN = List.of("token", "secret", "password", "authorization", "cookie", "credential");
    private final ObjectMapper mapper;
    public AssetMetadataSecurity(ObjectMapper mapper) { this.mapper = mapper; }

    public Map<String, Object> validateAndCanonicalize(Map<String, Object> metadata) {
        if (metadata == null) return null;
        Map<String, Object> result = canonicalMap(metadata);
        if (mapper.writeValueAsBytes(result).length > MAX_BYTES) {
            throw new AssetValidationException("providerMetadata", "providerMetadata exceeds 16384 UTF-8 bytes");
        }
        return result;
    }

    public String fingerprint(Map<String, Object> metadata) {
        if (metadata == null) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonicalMap(metadata)));
            return "[SHA256:" + java.util.HexFormat.of().formatHex(digest) + "]";
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private Map<String, Object> canonicalMap(Map<?, ?> input) {
        Map<String, Object> sorted = new TreeMap<>();
        input.forEach((rawKey, value) -> {
            String key = String.valueOf(rawKey);
            String normalized = key.toLowerCase(Locale.ROOT);
            if (FORBIDDEN.stream().anyMatch(normalized::contains)) {
                throw new AssetValidationException("providerMetadata", "providerMetadata contains a forbidden key");
            }
            sorted.put(key, canonicalValue(value));
        });
        return new LinkedHashMap<>(sorted);
    }

    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) return canonicalMap(map);
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(canonicalValue(item)));
            return result;
        }
        return value;
    }
}
