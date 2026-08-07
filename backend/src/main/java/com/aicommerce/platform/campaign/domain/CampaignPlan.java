package com.aicommerce.platform.campaign.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.aicommerce.platform.common.persistence.ArchivableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected CampaignPlan() {
    }

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

    public UUID getCampaignUuid() { return campaignUuid; }
    public String getCampaignName() { return campaignName; }
    public String getActivityType() { return activityType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getObjective() { return objective; }
    public String getPlatform() { return platform; }
    public BigDecimal getBudgetDaily() { return budgetDaily; }
    public BigDecimal getBudgetTotal() { return budgetTotal; }
    public String getCurrency() { return currency; }
    public String getPromotion() { return promotion; }
    public String getLandingPage() { return landingPage; }
}
