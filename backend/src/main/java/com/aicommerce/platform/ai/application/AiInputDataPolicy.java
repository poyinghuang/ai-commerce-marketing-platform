package com.aicommerce.platform.ai.application;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tools.jackson.databind.JsonNode;

final class AiInputDataPolicy {

    private static final List<String> FORBIDDEN_KEYS = List.of(
            "credential", "secret", "token", "providerurl", "baseurl", "apikey", "password",
            "authorization", "cookie", "header", "environment", "customer", "order", "payment",
            "cardnumber", "cvv", "email", "phone", "address");

    private AiInputDataPolicy() {
    }

    static void validateSchema(JsonNode schema) {
        validateNoForbiddenKeys(schema, "inputSchema");
    }

    static void validateSnapshot(JsonNode snapshot, JsonNode schema) {
        validateNoForbiddenKeys(snapshot, "inputSnapshot");
        Set<String> allowedKeys = allowedTopLevelKeys(schema);
        for (String key : snapshot.propertyNames()) {
            if (!allowedKeys.contains(key)) {
                throw new AiFoundationValidationException(
                        "inputSnapshot contains a field not allowlisted by the prompt template version");
            }
        }
    }

    private static Set<String> allowedTopLevelKeys(JsonNode schema) {
        JsonNode properties = schema.get("properties");
        JsonNode source = properties != null && properties.isObject() ? properties : schema;
        Set<String> keys = new HashSet<>();
        source.propertyNames().forEach(keys::add);
        return Set.copyOf(keys);
    }

    private static void validateNoForbiddenKeys(JsonNode node, String field) {
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (FORBIDDEN_KEYS.stream().anyMatch(normalized::contains)) {
                    throw new AiFoundationValidationException(field + " contains a prohibited sensitive-data key");
                }
                validateNoForbiddenKeys(node.get(name), field);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) validateNoForbiddenKeys(child, field);
        }
    }
}
