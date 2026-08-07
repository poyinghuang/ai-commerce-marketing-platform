package com.aicommerce.platform.campaign.domain;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "campaign_plans")
public class CampaignPlan extends ArchivableEntity {

  @Id
  @Column(name = "campaign_uuid", nullable = false, updatable = false)
  private UUID campaignUuid;

  @Column(name = "campaign_name", nullable = false, length = 256)
  private String campaignName;

  @Column(name = "activity_type", length = 64)
  private String activityType;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "objective", length = 2000)
  private String objective;

  @Column(name = "platform", length = 64)
  private String platform;

  @Column(name = "budget_daily", precision = 19, scale = 4)
  private BigDecimal budgetDaily;

  @Column(name = "budget_total", precision = 19, scale = 4)
  private BigDecimal budgetTotal;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "promotion", length = 2000)
  private String promotion;

  @Column(name = "landing_page", length = 2048)
  private String landingPage;

  protected CampaignPlan() {}

  private CampaignPlan(UUID campaignUuid, String campaignName) {
    this.campaignUuid = Objects.requireNonNull(campaignUuid, "campaignUuid is required");
    if (campaignName == null || campaignName.isBlank()) {
      throw new IllegalArgumentException("campaignName is required");
    }
    this.campaignName = campaignName.trim();
  }

  public static CampaignPlan create(UUID campaignUuid, String campaignName) {
    return new CampaignPlan(campaignUuid, campaignName);
  }

  public void update(
      String campaignName,
      String activityType,
      LocalDate startDate,
      LocalDate endDate,
      String objective,
      String platform,
      BigDecimal budgetDaily,
      BigDecimal budgetTotal,
      String currency,
      String promotion,
      String landingPage) {
    if (getLifecycleStatus() == com.aicommerce.platform.common.domain.LifecycleStatus.ARCHIVED) {
      throw new IllegalStateException("Archived campaign cannot be modified");
    }
    this.campaignName = required(campaignName, "campaignName", 256);
    this.activityType = optional(activityType, "activityType", 64);
    if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
      throw new IllegalArgumentException("endDate must be on or after startDate");
    }
    this.startDate = startDate;
    this.endDate = endDate;
    this.objective = optional(objective, "objective", 2000);
    this.platform = optional(platform, "platform", 64);
    validateMoney(budgetDaily, "budgetDaily");
    validateMoney(budgetTotal, "budgetTotal");
    String normalizedCurrency = optional(currency, "currency", 3);
    if ((budgetDaily != null || budgetTotal != null) && normalizedCurrency == null) {
      throw new IllegalArgumentException("currency is required when a budget is provided");
    }
    if (normalizedCurrency != null) {
      if (!normalizedCurrency.matches("[A-Z]{3}"))
        throw new IllegalArgumentException("currency must be a three-letter uppercase code");
      try {
        Currency.getInstance(normalizedCurrency);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("currency must be a valid ISO currency code", e);
      }
    }
    this.budgetDaily = budgetDaily == null ? null : budgetDaily.setScale(4);
    this.budgetTotal = budgetTotal == null ? null : budgetTotal.setScale(4);
    this.currency = normalizedCurrency;
    this.promotion = optional(promotion, "promotion", 2000);
    this.landingPage = url(landingPage);
  }

  private static void validateMoney(BigDecimal value, String field) {
    if (value != null
        && (value.signum() < 0 || value.scale() > 4 || value.precision() - value.scale() > 15))
      throw new IllegalArgumentException(field + " exceeds non-negative numeric(19,4)");
  }

  private static String url(String value) {
    String normalized = optional(value, "landingPage", 2048);
    if (normalized == null) return null;
    try {
      URI uri = new URI(normalized);
      if (!uri.isAbsolute()
          || !("http".equalsIgnoreCase(uri.getScheme())
              || "https".equalsIgnoreCase(uri.getScheme())))
        throw new IllegalArgumentException("landingPage must be an absolute HTTP(S) URL");
      return normalized;
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("landingPage must be a valid URL", e);
    }
  }

  private static String required(String value, String field, int max) {
    String result = optional(value, field, max);
    if (result == null) throw new IllegalArgumentException(field + " is required");
    return result;
  }

  private static String optional(String value, String field, int max) {
    if (value == null || value.isBlank()) return null;
    String result = value.trim();
    if (result.length() > max)
      throw new IllegalArgumentException(field + " exceeds " + max + " characters");
    return result;
  }

  public UUID getCampaignUuid() {
    return campaignUuid;
  }

  public String getCampaignName() {
    return campaignName;
  }

  public String getActivityType() {
    return activityType;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public String getObjective() {
    return objective;
  }

  public String getPlatform() {
    return platform;
  }

  public BigDecimal getBudgetDaily() {
    return budgetDaily;
  }

  public BigDecimal getBudgetTotal() {
    return budgetTotal;
  }

  public String getCurrency() {
    return currency;
  }

  public String getPromotion() {
    return promotion;
  }

  public String getLandingPage() {
    return landingPage;
  }
}
