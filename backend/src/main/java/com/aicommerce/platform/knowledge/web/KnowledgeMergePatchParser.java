package com.aicommerce.platform.knowledge.web;
import java.util.Locale;
import java.util.Set;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.knowledge.application.KnowledgeValidationException;
import com.aicommerce.platform.knowledge.application.PatchKnowledgeCommand;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
@Component
public class KnowledgeMergePatchParser {
    private static final Set<String> FIELDS = Set.of("knowledgeType", "title", "content", "source");
    public PatchKnowledgeCommand parse(JsonNode patch) {
        if (patch == null || !patch.isObject()) throw new InvalidMergePatchException("JSON Merge Patch must be an object");
        for (String field : patch.propertyNames()) if (!FIELDS.contains(field)) throw new InvalidMergePatchException("Field is not mutable: " + field);
        return new PatchKnowledgeCommand(type(patch), text(patch,"title",false), text(patch,"content",false), text(patch,"source",true));
    }
    private FieldPatch<KnowledgeType> type(JsonNode patch) {
        if (!patch.has("knowledgeType")) return FieldPatch.absent();
        JsonNode value = patch.get("knowledgeType");
        if (value.isNull() || !value.isString()) throw new KnowledgeValidationException("knowledgeType", "knowledgeType must be a string");
        try { return FieldPatch.present(KnowledgeType.valueOf(value.stringValue().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { throw new KnowledgeValidationException("knowledgeType", "knowledgeType is invalid"); }
    }
    private FieldPatch<String> text(JsonNode patch, String field, boolean nullable) {
        if (!patch.has(field)) return FieldPatch.absent(); JsonNode value = patch.get(field);
        if (value.isNull()) { if (!nullable) throw new KnowledgeValidationException(field, field + " cannot be null"); return FieldPatch.present(null); }
        if (!value.isString()) throw new KnowledgeValidationException(field, field + " must be a string or null");
        if (!nullable && value.stringValue().isBlank()) throw new KnowledgeValidationException(field, field + " is required");
        return FieldPatch.present(value.stringValue());
    }
}
