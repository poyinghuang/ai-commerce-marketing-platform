package com.aicommerce.platform.campaign.web;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class CampaignMergePatchParser {
  private static final Set<String> FIELDS =
      Set.of(
          "campaignName",
          "activityType",
          "startDate",
          "endDate",
          "objective",
          "platform",
          "budgetDaily",
          "budgetTotal",
          "currency",
          "promotion",
          "landingPage");

  public PatchCampaignCommand parse(JsonNode p) {
    object(p);
    for (String f : p.propertyNames())
      if (!FIELDS.contains(f)) throw new InvalidMergePatchException("Field is not mutable: " + f);
    return new PatchCampaignCommand(
        required(p, "campaignName"),
        text(p, "activityType"),
        date(p, "startDate"),
        date(p, "endDate"),
        text(p, "objective"),
        text(p, "platform"),
        decimal(p, "budgetDaily"),
        decimal(p, "budgetTotal"),
        text(p, "currency"),
        text(p, "promotion"),
        text(p, "landingPage"));
  }

  private void object(JsonNode p) {
    if (p == null || !p.isObject())
      throw new InvalidMergePatchException("JSON Merge Patch must be an object");
  }

  private FieldPatch<String> required(JsonNode p, String n) {
    FieldPatch<String> f = text(p, n);
    if (f.present() && (f.value() == null || f.value().isBlank()))
      throw new CampaignValidationException(n, n + " is required");
    return f;
  }

  private FieldPatch<String> text(JsonNode p, String n) {
    if (!p.has(n)) return FieldPatch.absent();
    JsonNode v = p.get(n);
    if (v.isNull()) return FieldPatch.present(null);
    if (!v.isString()) throw new CampaignValidationException(n, n + " must be a string or null");
    return FieldPatch.present(v.stringValue());
  }

  private FieldPatch<BigDecimal> decimal(JsonNode p, String n) {
    if (!p.has(n)) return FieldPatch.absent();
    JsonNode v = p.get(n);
    if (v.isNull()) return FieldPatch.present(null);
    if (!v.isNumber()) throw new CampaignValidationException(n, n + " must be a number or null");
    return FieldPatch.present(v.decimalValue());
  }

  private FieldPatch<LocalDate> date(JsonNode p, String n) {
    FieldPatch<String> f = text(p, n);
    if (!f.present()) return FieldPatch.absent();
    if (f.value() == null) return FieldPatch.present(null);
    try {
      return FieldPatch.present(LocalDate.parse(f.value()));
    } catch (DateTimeParseException e) {
      throw new CampaignValidationException(n, n + " must use YYYY-MM-DD");
    }
  }
}
