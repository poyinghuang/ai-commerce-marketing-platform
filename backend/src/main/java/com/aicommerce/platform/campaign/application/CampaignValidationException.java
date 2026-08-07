package com.aicommerce.platform.campaign.application;

public class CampaignValidationException extends RuntimeException {
  private final String field;

  public CampaignValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
