package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CampaignProductMergePatchParser {
  private static final Set<String> FIELDS = Set.of("role", "priority", "budgetWeight");

  public PatchCampaignProductCommand parse(JsonNode p) {
    if (p == null || !p.isObject())
      throw new InvalidMergePatchException("JSON Merge Patch must be an object");
    for (String f : p.propertyNames())
      if (!FIELDS.contains(f)) throw new InvalidMergePatchException("Field is not mutable: " + f);
    return new PatchCampaignProductCommand(
        text(p, "role"), integer(p, "priority"), decimal(p, "budgetWeight"));
  }

  private FieldPatch<String> text(JsonNode p, String n) {
    if (!p.has(n)) return FieldPatch.absent();
    JsonNode v = p.get(n);
    if (v.isNull()) return FieldPatch.present(null);
    if (!v.isString()) throw new CampaignValidationException(n, n + " must be a string or null");
    return FieldPatch.present(v.stringValue());
  }

  private FieldPatch<Integer> integer(JsonNode p, String n) {
    if (!p.has(n)) return FieldPatch.absent();
    JsonNode v = p.get(n);
    if (v.isNull()) return FieldPatch.present(null);
    if (!v.isIntegralNumber() || !v.canConvertToInt())
      throw new CampaignValidationException(n, n + " must be an integer or null");
    return FieldPatch.present(v.intValue());
  }

  private FieldPatch<BigDecimal> decimal(JsonNode p, String n) {
    if (!p.has(n)) return FieldPatch.absent();
    JsonNode v = p.get(n);
    if (v.isNull()) return FieldPatch.present(null);
    if (!v.isNumber()) throw new CampaignValidationException(n, n + " must be a number or null");
    return FieldPatch.present(v.decimalValue());
  }
}
