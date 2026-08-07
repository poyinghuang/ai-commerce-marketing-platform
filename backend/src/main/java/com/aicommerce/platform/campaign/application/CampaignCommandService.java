package com.aicommerce.platform.campaign.application;

import com.aicommerce.platform.audit.application.*;
import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.campaign.domain.*;
import com.aicommerce.platform.campaign.infrastructure.persistence.*;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.*;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignCommandService {
  private final CampaignPlanJpaRepository campaigns;
  private final CampaignProductJpaRepository associations;
  private final ProductJpaRepository products;
  private final AuditOperationContextFactory contexts;
  private final AuditWriter audit;
  private final CampaignAuditChangeFactory changes;
  private final ProductQualityRecalculationService quality;
  private final Clock clock;

  public CampaignCommandService(
      CampaignPlanJpaRepository c,
      CampaignProductJpaRepository a,
      ProductJpaRepository p,
      AuditOperationContextFactory x,
      AuditWriter w,
      CampaignAuditChangeFactory f,
      ProductQualityRecalculationService quality,
      Clock clock) {
    campaigns = c;
    associations = a;
    products = p;
    contexts = x;
    audit = w;
    changes = f;
    this.quality = quality;
    this.clock = clock;
  }

  @Transactional
  public CampaignPlan create(CreateCampaignCommand x, String requestId) {
    AuditOperationContext context = context(requestId);
    CampaignPlan c;
    try {
      c = CampaignPlan.create(UUID.randomUUID(), x.campaignName());
      c.update(
          x.campaignName(),
          x.activityType(),
          x.startDate(),
          x.endDate(),
          x.objective(),
          x.platform(),
          x.budgetDaily(),
          x.budgetTotal(),
          x.currency(),
          x.promotion(),
          x.landingPage());
    } catch (IllegalArgumentException e) {
      throw validation(e);
    }
    c = campaigns.saveAndFlush(c);
    append(c, context, AuditAction.CREATE, changes.campaignCreate(CampaignSnapshot.from(c)));
    return c;
  }

  @Transactional
  public CampaignPlan patch(UUID id, long version, PatchCampaignCommand x, String requestId) {
    AuditOperationContext context = context(requestId);
    CampaignPlan c = findCampaign(id);
    version(c, version);
    if (c.getLifecycleStatus() == LifecycleStatus.ARCHIVED) throw new CampaignArchivedException();
    CampaignSnapshot before = CampaignSnapshot.from(c);
    try {
      c.update(
          x.campaignName().resolve(c.getCampaignName()),
          x.activityType().resolve(c.getActivityType()),
          x.startDate().resolve(c.getStartDate()),
          x.endDate().resolve(c.getEndDate()),
          x.objective().resolve(c.getObjective()),
          x.platform().resolve(c.getPlatform()),
          x.budgetDaily().resolve(c.getBudgetDaily()),
          x.budgetTotal().resolve(c.getBudgetTotal()),
          x.currency().resolve(c.getCurrency()),
          x.promotion().resolve(c.getPromotion()),
          x.landingPage().resolve(c.getLandingPage()));
    } catch (IllegalArgumentException e) {
      throw validation(e);
    }
    List<AuditChange> actual = changes.campaign(before, CampaignSnapshot.from(c));
    if (actual.isEmpty()) return c;
    c = campaigns.saveAndFlush(c);
    append(c, context, AuditAction.UPDATE, actual);
    recalculateCampaignProducts(id, context);
    return c;
  }

  @Transactional
  public CampaignPlan archive(UUID id, long version, String requestId) {
    AuditOperationContext context = context(requestId);
    CampaignPlan c = findCampaign(id);
    version(c, version);
    CampaignSnapshot before = CampaignSnapshot.from(c);
    if (!c.archive(Instant.now(clock))) return c;
    c = campaigns.saveAndFlush(c);
    append(c, context, AuditAction.ARCHIVE, changes.campaign(before, CampaignSnapshot.from(c)));
    recalculateCampaignProducts(id, context);
    return c;
  }

  @Transactional
  public CampaignPlan restore(UUID id, long version, String requestId) {
    AuditOperationContext context = context(requestId);
    CampaignPlan c = findCampaign(id);
    version(c, version);
    CampaignSnapshot before = CampaignSnapshot.from(c);
    if (!c.restore()) return c;
    c = campaigns.saveAndFlush(c);
    append(c, context, AuditAction.RESTORE, changes.campaign(before, CampaignSnapshot.from(c)));
    recalculateCampaignProducts(id, context);
    return c;
  }

  @Transactional
  public CampaignProduct addProduct(
      UUID campaignUuid, CreateCampaignProductCommand x, String requestId) {
    AuditOperationContext context = context(requestId);
    requireActiveCampaign(campaignUuid);
    requireActiveProduct(x.productUuid());
    if (associations.findByCampaignUuidAndProductUuid(campaignUuid, x.productUuid()).isPresent())
      throw new RelationshipConflictException();
    CampaignProduct cp;
    try {
      cp = CampaignProduct.create(UUID.randomUUID(), campaignUuid, x.productUuid());
      cp.update(x.role(), x.priority(), x.budgetWeight());
      cp = associations.saveAndFlush(cp);
    } catch (DataIntegrityViolationException e) {
      throw new RelationshipConflictException();
    } catch (IllegalArgumentException e) {
      throw validation(e);
    }
    append(
        cp, context, AuditAction.CREATE, changes.productCreate(CampaignProductSnapshot.from(cp)));
    quality.recalculate(x.productUuid(), context);
    return cp;
  }

  @Transactional
  public CampaignProduct patchProduct(
      UUID c, UUID p, long v, PatchCampaignProductCommand x, String requestId) {
    AuditOperationContext context = context(requestId);
    requireActiveCampaign(c);
    requireActiveProduct(p);
    CampaignProduct cp = findProduct(c, p);
    version(cp, v);
    if (cp.getLifecycleStatus() == LifecycleStatus.ARCHIVED)
      throw new CampaignProductArchivedException();
    CampaignProductSnapshot before = CampaignProductSnapshot.from(cp);
    try {
      cp.update(
          x.role().resolve(cp.getRole()),
          x.priority().resolve(cp.getPriority()),
          x.budgetWeight().resolve(cp.getBudgetWeight()));
    } catch (IllegalArgumentException e) {
      throw validation(e);
    }
    List<AuditChange> actual = changes.product(before, CampaignProductSnapshot.from(cp));
    if (actual.isEmpty()) return cp;
    cp = associations.saveAndFlush(cp);
    append(cp, context, AuditAction.UPDATE, actual);
    quality.recalculate(p, context);
    return cp;
  }

  @Transactional
  public CampaignProduct archiveProduct(UUID c, UUID p, long v, String requestId) {
    AuditOperationContext context = context(requestId);
    requireActiveCampaign(c);
    requireActiveProduct(p);
    CampaignProduct cp = findProduct(c, p);
    version(cp, v);
    CampaignProductSnapshot before = CampaignProductSnapshot.from(cp);
    if (!cp.archive(Instant.now(clock))) return cp;
    cp = associations.saveAndFlush(cp);
    append(
        cp,
        context,
        AuditAction.ARCHIVE,
        changes.product(before, CampaignProductSnapshot.from(cp)));
    quality.recalculate(p, context);
    return cp;
  }

  @Transactional
  public CampaignProduct restoreProduct(UUID c, UUID p, long v, String requestId) {
    AuditOperationContext context = context(requestId);
    requireActiveCampaign(c);
    requireActiveProduct(p);
    CampaignProduct cp = findProduct(c, p);
    version(cp, v);
    CampaignProductSnapshot before = CampaignProductSnapshot.from(cp);
    if (!cp.restore()) return cp;
    cp = associations.saveAndFlush(cp);
    append(
        cp,
        context,
        AuditAction.RESTORE,
        changes.product(before, CampaignProductSnapshot.from(cp)));
    quality.recalculate(p, context);
    return cp;
  }

  private CampaignPlan findCampaign(UUID id) {
    return campaigns.findForMutation(id).orElseThrow(CampaignNotFoundException::new);
  }

  private CampaignProduct findProduct(UUID c, UUID p) {
    return associations.findForMutation(c, p).orElseThrow(CampaignProductNotFoundException::new);
  }

  private CampaignPlan requireActiveCampaign(UUID id) {
    CampaignPlan c = findCampaign(id);
    if (c.getLifecycleStatus() == LifecycleStatus.ARCHIVED) throw new CampaignArchivedException();
    return c;
  }

  private Product requireActiveProduct(UUID id) {
    Product p =
        products.findForKnowledgeMutation(id).orElseThrow(() -> new ProductNotFoundException(id));
    if (p.getLifecycleStatus() == ProductLifecycleStatus.ARCHIVED)
      throw new ProductArchivedException();
    return p;
  }

  private void version(CampaignPlan c, long v) {
    if (c.getVersion() != v) throw new CampaignPreconditionFailedException();
  }

  private void version(CampaignProduct c, long v) {
    if (c.getVersion() != v) throw new CampaignPreconditionFailedException();
  }

  private AuditOperationContext context(String id) {
    try {
      return contexts.forCurrentActor(id);
    } catch (IllegalStateException e) {
      throw new AuditActorUnavailableException(e);
    }
  }

  private void append(CampaignPlan c, AuditOperationContext x, AuditAction a, List<AuditChange> d) {
    if (!d.isEmpty())
      audit.append(
          new AuditEvent(
              UUID.randomUUID(),
              x,
              a,
              "CAMPAIGN_PLAN",
              c.getCampaignUuid(),
              null,
              Instant.now(clock),
              d));
  }

  private void append(
      CampaignProduct c, AuditOperationContext x, AuditAction a, List<AuditChange> d) {
    if (!d.isEmpty())
      audit.append(
          new AuditEvent(
              UUID.randomUUID(),
              x,
              a,
              "CAMPAIGN_PRODUCT",
              c.getCampaignProductUuid(),
              c.getProductUuid(),
              Instant.now(clock),
              d));
  }

  private CampaignValidationException validation(IllegalArgumentException e) {
    String m = e.getMessage() == null ? "Campaign validation failed" : e.getMessage();
    String f = m.contains(" ") ? m.substring(0, m.indexOf(' ')) : "campaign";
    return new CampaignValidationException(f, m);
  }

  private void recalculateCampaignProducts(UUID campaignUuid, AuditOperationContext context) {
    associations.findActiveProductUuids(campaignUuid).forEach(productUuid -> quality.recalculate(productUuid, context));
  }
}
