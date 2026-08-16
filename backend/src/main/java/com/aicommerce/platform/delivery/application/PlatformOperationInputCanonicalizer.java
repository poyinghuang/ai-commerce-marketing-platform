package com.aicommerce.platform.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PlatformOperationInputCanonicalizer {
    private static final int MAX_PAYLOAD_BYTES = 16_384;
    private static final List<String> FORBIDDEN_KEYS = List.of(
            "authorization", "cookie", "credential", "password", "secret", "token",
            "access_token", "app_secret", "provider_url", "graph_url", "raw_request", "raw_response");

    private final ObjectMapper mapper;

    public PlatformOperationInputCanonicalizer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CanonicalInput canonicalize(String json) {
        try {
            Object parsed = mapper.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("normalized request must be a JSON object");
            }
            Object normalized = normalize(parsed, new ArrayList<>());
            String canonical = mapper.writeValueAsString(normalized);
            if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("normalized request exceeds 16384 bytes");
            }
            return new CanonicalInput(canonical, sha256(canonical));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("normalized request is invalid JSON", exception);
        }
    }

    public String idempotencyKey(String scope) {
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("idempotency scope is required");
        return sha256(scope);
    }

    private Object normalize(Object value, List<String> path) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException("normalized request keys must be non-blank strings");
                }
                rejectSensitiveKey(key, path);
                List<String> childPath = new ArrayList<>(path);
                childPath.add(key);
                sorted.put(key, normalize(entry.getValue(), childPath));
            }
            return sorted;
        }
        if (value instanceof List<?> list) return list.stream().map(item -> normalize(item, path)).toList();
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        throw new IllegalArgumentException("normalized request contains an unsupported value");
    }

    private void rejectSensitiveKey(String key, List<String> path) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        boolean forbidden = FORBIDDEN_KEYS.stream()
                .map(marker -> marker.replaceAll("[^a-z0-9]", ""))
                .anyMatch(normalized::contains);
        if (forbidden) throw new IllegalArgumentException("sensitive field is forbidden in normalized request");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CanonicalInput(String json, String sha256) { }
}
