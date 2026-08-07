package com.aicommerce.platform.quality.web;

import java.util.Set;

import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import com.aicommerce.platform.quality.application.ManualAdjustmentPatch;
import com.aicommerce.platform.quality.application.QualityValidationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class QualityMergePatchParser {
    private static final Set<String> FIELDS = Set.of("manualAdjustment", "reason");
    public ManualAdjustmentPatch parse(JsonNode patch) {
        if (patch == null || !patch.isObject()) throw new InvalidMergePatchException("JSON Merge Patch must be an object");
        for (String field : patch.propertyNames()) {
            if (!FIELDS.contains(field)) throw new InvalidMergePatchException("Field is not mutable: " + field);
        }
        return new ManualAdjustmentPatch(adjustment(patch), reason(patch));
    }
    private FieldPatch<Integer> adjustment(JsonNode patch) {
        if (!patch.has("manualAdjustment")) return FieldPatch.absent();
        JsonNode value = patch.get("manualAdjustment");
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new QualityValidationException("manualAdjustment", "manualAdjustment must be an integer");
        }
        int result = value.intValue();
        if (result < -20 || result > 20) {
            throw new QualityValidationException("manualAdjustment", "manualAdjustment must be between -20 and 20");
        }
        return FieldPatch.present(result);
    }
    private FieldPatch<String> reason(JsonNode patch) {
        if (!patch.has("reason")) return FieldPatch.absent();
        JsonNode value = patch.get("reason");
        if (value.isNull()) return FieldPatch.present(null);
        if (!value.isString()) throw new QualityValidationException("reason", "reason must be a string or null");
        if (value.stringValue().length() > 1000) throw new QualityValidationException("reason", "reason exceeds 1000 characters");
        return FieldPatch.present(value.stringValue());
    }
}
