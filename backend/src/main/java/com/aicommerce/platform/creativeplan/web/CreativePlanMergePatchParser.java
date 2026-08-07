package com.aicommerce.platform.creativeplan.web;

import java.util.Set;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.creativeplan.application.CreativePlanValidationException;
import com.aicommerce.platform.creativeplan.application.PatchCreativePlanCommand;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class CreativePlanMergePatchParser {
    private static final Set<String> FIELDS = Set.of("planName", "primaryAudience", "secondaryAudience", "painPoint",
            "coreBenefit", "creativeAngle", "emotionalDirection", "brandTone", "visualStyle", "mainColor",
            "characterSetting", "cta");
    public PatchCreativePlanCommand parse(JsonNode patch) {
        if (patch == null || !patch.isObject()) throw new InvalidMergePatchException("JSON Merge Patch must be an object");
        for (String field : patch.propertyNames()) if (!FIELDS.contains(field)) throw new InvalidMergePatchException("Field is not mutable: " + field);
        return new PatchCreativePlanCommand(required(patch, "planName"), field(patch,"primaryAudience"),
                field(patch,"secondaryAudience"), field(patch,"painPoint"), field(patch,"coreBenefit"),
                field(patch,"creativeAngle"), field(patch,"emotionalDirection"), field(patch,"brandTone"),
                field(patch,"visualStyle"), field(patch,"mainColor"), field(patch,"characterSetting"), field(patch,"cta"));
    }
    private FieldPatch<String> required(JsonNode patch, String name) {
        FieldPatch<String> value = field(patch, name);
        if (value.present() && (value.value() == null || value.value().isBlank())) throw new CreativePlanValidationException(name, name + " is required");
        return value;
    }
    private FieldPatch<String> field(JsonNode patch, String name) {
        if (!patch.has(name)) return FieldPatch.absent();
        JsonNode value = patch.get(name);
        if (value.isNull()) return FieldPatch.present(null);
        if (!value.isString()) throw new CreativePlanValidationException(name, name + " must be a string or null");
        return FieldPatch.present(value.stringValue());
    }
}
