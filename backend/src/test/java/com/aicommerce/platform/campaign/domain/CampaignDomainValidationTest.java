package com.aicommerce.platform.campaign.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignDomainValidationTest {

  @Test
  void acceptsAllApprovedWritableFieldCategoriesAtTheirBoundaries() {
    CampaignPlan campaign = CampaignPlan.create(UUID.randomUUID(), "x".repeat(256));
    assertThatCode(
            () ->
                campaign.update(
                    "x".repeat(256),
                    "a".repeat(64),
                    LocalDate.parse("2026-08-01"),
                    LocalDate.parse("2026-08-01"),
                    "o".repeat(2000),
                    "p".repeat(64),
                    new BigDecimal("999999999999999.9999"),
                    BigDecimal.ZERO,
                    "USD",
                    "r".repeat(2000),
                    "https://example.test/landing"))
        .doesNotThrowAnyException();
    CampaignProduct association =
        CampaignProduct.create(UUID.randomUUID(), campaign.getCampaignUuid(), UUID.randomUUID());
    assertThatCode(() -> association.update("r".repeat(128), 0, new BigDecimal("100.00")))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsStringLengthsAndDateOrder() {
    assertInvalidCampaign(" ", null, null, null, null, null, null, null, null, null, null);
    assertInvalidCampaign(
        "x".repeat(257), null, null, null, null, null, null, null, null, null, null);
    assertInvalidCampaign(
        "ok", "x".repeat(65), null, null, null, null, null, null, null, null, null);
    assertInvalidCampaign(
        "ok",
        null,
        LocalDate.parse("2026-08-02"),
        LocalDate.parse("2026-08-01"),
        null,
        null,
        null,
        null,
        null,
        null,
        null);
    assertInvalidCampaign(
        "ok", null, null, null, "x".repeat(2001), null, null, null, null, null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, "x".repeat(65), null, null, null, null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, null, null, null, "x".repeat(2001), null);
  }

  @Test
  void rejectsBudgetScaleRangeCurrencyAndLandingPage() {
    assertInvalidCampaign(
        "ok", null, null, null, null, null, new BigDecimal("-0.0001"), null, "USD", null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, new BigDecimal("1.00001"), null, "USD", null, null);
    assertInvalidCampaign(
        "ok",
        null,
        null,
        null,
        null,
        null,
        new BigDecimal("1000000000000000.0000"),
        null,
        "USD",
        null,
        null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, BigDecimal.ONE, null, null, null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, BigDecimal.ONE, null, "usd", null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, BigDecimal.ONE, null, "ZZZ", null, null);
    assertInvalidCampaign(
        "ok", null, null, null, null, null, null, null, null, null, "javascript:alert(1)");
    assertInvalidCampaign(
        "ok", null, null, null, null, null, null, null, null, null, "relative/path");
  }

  @Test
  void rejectsAssociationRolePriorityAndBudgetWeightBoundaries() {
    CampaignProduct association =
        CampaignProduct.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    assertThatThrownBy(() -> association.update("x".repeat(129), null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> association.update(null, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> association.update(null, null, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> association.update(null, null, new BigDecimal("100.01")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> association.update(null, null, new BigDecimal("1.001")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void assertInvalidCampaign(
      String name,
      String activity,
      LocalDate start,
      LocalDate end,
      String objective,
      String platform,
      BigDecimal daily,
      BigDecimal total,
      String currency,
      String promotion,
      String url) {
    CampaignPlan campaign = CampaignPlan.create(UUID.randomUUID(), "valid");
    assertThatThrownBy(
            () ->
                campaign.update(
                    name, activity, start, end, objective, platform, daily, total, currency,
                    promotion, url))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
