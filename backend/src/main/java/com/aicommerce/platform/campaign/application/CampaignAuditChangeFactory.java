package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.audit.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CampaignAuditChangeFactory {
  public List<AuditChange> campaignCreate(CampaignSnapshot a) {
    return campaign(null, a);
  }

  public List<AuditChange> campaign(CampaignSnapshot b, CampaignSnapshot a) {
    List<AuditChange> r = new ArrayList<>();
    add(
        r,
        "campaign_name",
        s(b == null ? null : b.campaignName()),
        s(a.campaignName()),
        AuditValueType.STRING);
    add(
        r,
        "activity_type",
        s(b == null ? null : b.activityType()),
        s(a.activityType()),
        AuditValueType.STRING);
    add(
        r,
        "start_date",
        s(b == null ? null : b.startDate()),
        s(a.startDate()),
        AuditValueType.DATE);
    add(r, "end_date", s(b == null ? null : b.endDate()), s(a.endDate()), AuditValueType.DATE);
    add(
        r,
        "objective",
        s(b == null ? null : b.objective()),
        s(a.objective()),
        AuditValueType.STRING);
    add(r, "platform", s(b == null ? null : b.platform()), s(a.platform()), AuditValueType.STRING);
    add(
        r,
        "budget_daily",
        s(b == null ? null : b.budgetDaily()),
        s(a.budgetDaily()),
        AuditValueType.DECIMAL);
    add(
        r,
        "budget_total",
        s(b == null ? null : b.budgetTotal()),
        s(a.budgetTotal()),
        AuditValueType.DECIMAL);
    add(r, "currency", s(b == null ? null : b.currency()), s(a.currency()), AuditValueType.STRING);
    add(
        r,
        "promotion",
        s(b == null ? null : b.promotion()),
        s(a.promotion()),
        AuditValueType.STRING);
    add(
        r,
        "landing_page",
        s(b == null ? null : b.landingPage()),
        s(a.landingPage()),
        AuditValueType.STRING);
    lifecycle(
        r,
        b == null ? null : b.lifecycleStatus(),
        a.lifecycleStatus(),
        b == null ? null : b.archivedAt(),
        a.archivedAt());
    return List.copyOf(r);
  }

  public List<AuditChange> productCreate(CampaignProductSnapshot a) {
    return product(null, a);
  }

  public List<AuditChange> product(CampaignProductSnapshot b, CampaignProductSnapshot a) {
    List<AuditChange> r = new ArrayList<>();
    add(
        r,
        "campaign_uuid",
        s(b == null ? null : b.campaignUuid()),
        s(a.campaignUuid()),
        AuditValueType.UUID);
    add(
        r,
        "product_uuid",
        s(b == null ? null : b.productUuid()),
        s(a.productUuid()),
        AuditValueType.UUID);
    add(r, "role", s(b == null ? null : b.role()), s(a.role()), AuditValueType.STRING);
    add(r, "priority", s(b == null ? null : b.priority()), s(a.priority()), AuditValueType.INTEGER);
    add(
        r,
        "budget_weight",
        s(b == null ? null : b.budgetWeight()),
        s(a.budgetWeight()),
        AuditValueType.DECIMAL);
    lifecycle(
        r,
        b == null ? null : b.lifecycleStatus(),
        a.lifecycleStatus(),
        b == null ? null : b.archivedAt(),
        a.archivedAt());
    return List.copyOf(r);
  }

  private void lifecycle(List<AuditChange> r, Object bs, Object as, Object ba, Object aa) {
    add(r, "lifecycle_status", s(bs), s(as), AuditValueType.ENUM);
    add(r, "archived_at", s(ba), s(aa), AuditValueType.TIMESTAMP);
  }

  private String s(Object v) {
    return v == null ? null : v.toString();
  }

  private void add(List<AuditChange> r, String f, String o, String n, AuditValueType t) {
    if (!Objects.equals(o, n)) r.add(new AuditChange(f, o, n, t, r.size()));
  }
}
