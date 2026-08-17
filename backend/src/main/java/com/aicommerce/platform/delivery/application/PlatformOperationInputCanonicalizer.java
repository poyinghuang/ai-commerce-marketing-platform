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
import java.util.Set;
import java.math.BigDecimal;
import java.text.Normalizer;

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
            validateContract((Map<?, ?>) normalized);
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
        if (value instanceof List<?>) throw new IllegalArgumentException("arrays are forbidden in normalized requests");
        if (value == null) throw new IllegalArgumentException("optional values must be omitted, not null");
        if (value instanceof String text) return Normalizer.normalize(text, Normalizer.Form.NFC);
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            if (decimal.scale() > 6) throw new IllegalArgumentException("numbers may have at most six fractional digits");
            return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
        }
        if (value instanceof Boolean) throw new IllegalArgumentException("booleans are not part of the platform command contract");
        throw new IllegalArgumentException("normalized request contains an unsupported value");
    }

    private void validateContract(Map<?, ?> value) {
        Object operation = value.get("operationType");
        if (!(operation instanceof String operationType)) {
            throw new IllegalArgumentException("operationType is required");
        }
        Set<String> generic = Set.of("schemaVersion", "operationType", "entityType", "entityUuid");
        Set<String> specific = switch (operationType) {
            case "CREATE_CAMPAIGN" -> Set.of("platformCampaignUuid", "campaignUuid", "objective",
                    "desiredState", "accountTimezone", "scheduleStart", "scheduleEnd");
            case "CREATE_AD_SET" -> Set.of("platformAdSetUuid", "platformCampaignUuid", "budgetType",
                    "budgetAmount", "currency", "accountTimezone", "optimizationGoal", "targetingProfileKey",
                    "placementProfileKey", "desiredState", "scheduleStart", "scheduleEnd");
            case "CREATE_AD" -> Set.of("platformAdUuid", "platformAdSetUuid", "productUuid", "assetUuid",
                    "generationOutputUuid", "reviewDecisionUuid", "approvedChecksumSha256", "creativeMappingKey",
                    "desiredState");
            case "PAUSE", "RESUME" -> Set.of("expectedEntityVersion", "targetDesiredState");
            case "UPDATE_BUDGET" -> Set.of("platformAdSetUuid", "expectedEntityVersion", "budgetType",
                    "currency", "previousBudgetAmount", "newBudgetAmount");
            default -> throw new IllegalArgumentException("unsupported operationType");
        };
        Set<String> allowed = new java.util.HashSet<>(generic);
        allowed.addAll(specific);
        if (!allowed.containsAll(value.keySet()) || !value.keySet().containsAll(generic)) {
            throw new IllegalArgumentException("normalized request contains unknown or missing generic keys");
        }
        Set<String> required = switch (operationType) {
            case "CREATE_CAMPAIGN" -> Set.of("platformCampaignUuid", "campaignUuid", "objective", "desiredState", "accountTimezone");
            case "CREATE_AD_SET" -> Set.of("platformAdSetUuid", "platformCampaignUuid", "budgetType", "budgetAmount", "currency", "accountTimezone", "optimizationGoal", "targetingProfileKey", "placementProfileKey", "desiredState");
            case "CREATE_AD" -> specific;
            case "PAUSE", "RESUME" -> specific;
            case "UPDATE_BUDGET" -> specific;
            default -> throw new IllegalArgumentException("unsupported operationType");
        };
        if (!value.keySet().containsAll(required)) throw new IllegalArgumentException("normalized request is missing required keys");
        if (value.containsKey("scheduleStart") != value.containsKey("scheduleEnd")) {
            throw new IllegalArgumentException("scheduleStart and scheduleEnd must be supplied together");
        }
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
