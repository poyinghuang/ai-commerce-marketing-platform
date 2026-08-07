package com.aicommerce.platform.campaign.web;

import static org.assertj.core.api.Assertions.*;

import com.aicommerce.platform.campaign.application.CampaignValidationException;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CampaignMergePatchParserTest {
  private final JsonMapper json = JsonMapper.builder().build();
  private final CampaignMergePatchParser campaigns = new CampaignMergePatchParser();
  private final CampaignProductMergePatchParser products = new CampaignProductMergePatchParser();

  @Test
  void preservesAbsentAndExplicitNull() throws Exception {
    var c =
        campaigns.parse(
            json.readTree(
                "{\"objective\":null,\"budgetDaily\":12.34,\"startDate\":\"2026-08-01\"}"));
    assertThat(c.campaignName().present()).isFalse();
    assertThat(c.objective().present()).isTrue();
    assertThat(c.objective().value()).isNull();
    assertThat(c.budgetDaily().value()).isEqualByComparingTo("12.34");
  }

  @Test
  void rejectsImmutableUnknownAndBadTypes() throws Exception {
    assertThatThrownBy(() -> campaigns.parse(json.readTree("{\"campaignUuid\":null}")))
        .isInstanceOf(InvalidMergePatchException.class);
    assertThatThrownBy(() -> campaigns.parse(json.readTree("{\"campaignName\":null}")))
        .isInstanceOf(CampaignValidationException.class);
    assertThatThrownBy(() -> products.parse(json.readTree("{\"productUuid\":null}")))
        .isInstanceOf(InvalidMergePatchException.class);
    assertThatThrownBy(() -> products.parse(json.readTree("{\"priority\":1.5}")))
        .isInstanceOf(CampaignValidationException.class);
  }
}
