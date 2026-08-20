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
        return canonicalizePersisted(json);
    }

    public CanonicalInput canonicalizePersisted(String json) {
        return canonicalizeInternal(json, false);
    }

    public CanonicalInput canonicalizeNewCreateAd(String json) {
        rejectNonCanonicalExpectedParentVersion(json);
        CanonicalInput input = canonicalizeInternal(json, true);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = mapper.readValue(input.json(), Map.class);
            if (!"CREATE_AD".equals(value.get("operationType")) || !value.containsKey("expectedParentVersion")
                    || !"APPROVED_IMAGE_ASSET_V1".equals(value.get("creativeMappingKey"))) {
                throw new IllegalArgumentException("CREATE_AD must use the new approved mapping shape");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("normalized request is invalid JSON", exception);
        }
        return input;
    }

    private CanonicalInput canonicalizeInternal(String json, boolean newCreateAd) {
        try {
            Object parsed = mapper.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("normalized request must be a JSON object");
            }
            Object normalized = normalize(parsed, new ArrayList<>());
            validateContract((Map<?, ?>) normalized, newCreateAd);
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

    private void validateContract(Map<?, ?> value, boolean newCreateAd) {
        Object operation = value.get("operationType");
        if (!(operation instanceof String operationType)) {
            throw new IllegalArgumentException("operationType is required");
        }
        Set<String> generic = Set.of("schemaVersion", "operationType", "entityType", "entityUuid");
        Set<String> createAdLegacy = Set.of("platformAdUuid", "platformAdSetUuid", "productUuid", "assetUuid",
                "generationOutputUuid", "reviewDecisionUuid", "approvedChecksumSha256", "creativeMappingKey",
                "desiredState");
        Set<String> createAdNew = new java.util.HashSet<>(createAdLegacy);
        createAdNew.add("expectedParentVersion");
        if ("CREATE_AD".equals(operationType)) {
            boolean hasParent = value.containsKey("expectedParentVersion");
            if (newCreateAd && !hasParent) throw new IllegalArgumentException("expectedParentVersion is required");
            if (hasParent && !canonicalNonNegativeLong(value.get("expectedParentVersion"))) {
                throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer");
            }
        }
        Set<String> specific = switch (operationType) {
            case "CREATE_CAMPAIGN" -> Set.of("platformCampaignUuid", "campaignUuid", "objective",
                    "desiredState", "accountTimezone", "scheduleStart", "scheduleEnd");
            case "CREATE_AD_SET" -> Set.of("platformAdSetUuid", "platformCampaignUuid", "budgetType",
                    "budgetAmount", "currency", "accountTimezone", "optimizationGoal", "targetingProfileKey",
                    "placementProfileKey", "desiredState", "scheduleStart", "scheduleEnd");
            case "CREATE_AD" -> value.containsKey("expectedParentVersion") || newCreateAd ? createAdNew : createAdLegacy;
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

    static void rejectNonCanonicalExpectedParentVersion(String json) {
        if (json == null) throw new IllegalArgumentException("normalized request is invalid JSON");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"expectedParentVersion\"\\s*:").matcher(json);
        if (!matcher.find()) throw new IllegalArgumentException("expectedParentVersion is required");
        int index = matcher.end();
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        if (index >= json.length()) throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer");
        char first = json.charAt(index);
        if (first == '"' || first == '[' || first == '{' || first == 't' || first == 'f' || first == 'n' || first == '+' || first == '-') {
            throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer");
        }
        int end = index;
        while (end < json.length() && "+-0123456789.eE".indexOf(json.charAt(end)) >= 0) end++;
        String token = json.substring(index, end);
        if (!token.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer");
        try {
            java.math.BigInteger value = new java.math.BigInteger(token);
            if (value.signum() < 0 || value.compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expectedParentVersion must be an unsigned integer", exception);
        }
    }

    private static boolean canonicalNonNegativeLong(Object value) {
        if (value instanceof Number number) {
            try {
                java.math.BigDecimal decimal = new java.math.BigDecimal(number.toString()).stripTrailingZeros();
                if (decimal.scale() > 0 || decimal.signum() < 0) return false;
                decimal.longValueExact();
                return true;
            } catch (ArithmeticException exception) {
                return false;
            }
        }
        return false;
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
