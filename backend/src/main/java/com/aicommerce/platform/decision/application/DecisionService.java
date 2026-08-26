package com.aicommerce.platform.decision.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditActorType;
import com.aicommerce.platform.audit.domain.AuditChange;
import com.aicommerce.platform.audit.domain.AuditEvent;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.decision.application.DecisionQueries.CampaignRow;
import com.aicommerce.platform.decision.application.DecisionQueries.RecommendationRow;
import com.aicommerce.platform.decision.application.DecisionQueries.StoredRecommendation;
import com.aicommerce.platform.decision.application.DecisionQueries.Window;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationDetailView;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationStatus;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationType;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage6.enabled:false}' == 'true'")
public class DecisionService {
    private final DecisionQueries queries;
    private final AuditOperationContextFactory contexts;
    private final AuditWriter audit;
    private final Clock clock;

    public DecisionService(DecisionQueries queries, AuditOperationContextFactory contexts, AuditWriter audit,
            Clock clock) {
        this.queries = queries;
        this.contexts = contexts;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public DecisionViews.GenerateView generate(String requestId) {
        AuditOperationContext context = actor(requestId);
        UUID account = queries.account();
        queries.lockAccount(account);
        Window window = queries.window();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        List<CampaignRow> campaigns = queries.eligibleCampaigns(account);
        long skipped = 0;
        long created = 0;
        long updated = 0;
        long replayed = 0;
        List<RecommendationView> changed = new ArrayList<>();
        for (CampaignRow campaign : campaigns) {
            Optional<DecisionQueries.SnapshotRow> snapshot = queries.latestCampaignSnapshot(
                    account, campaign.platformCampaignUuid(), window);
            if (snapshot.isEmpty()) {
                skipped++;
                continue;
            }
            DecisionQueries.SnapshotRow source = snapshot.get();
            RuleSetV1.Metrics metrics = DecisionQueries.metricsFrom(source);
            for (RuleSetV1.Emission emission : RuleSetV1.evaluate(metrics, campaign.desiredState())) {
                String fingerprint = EvidenceFingerprint.hash(campaign.campaignUuid(), source.sourceFingerprint(),
                        emission.type().name(), window.start(), window.end());
                RecommendationRow row = new RecommendationRow(UUID.randomUUID(), account,
                        campaign.platformCampaignUuid(), campaign.campaignUuid(), emission.type(), window,
                        campaign.desiredState(), emission.reasonSummary(), RuleSetV1.RISK, metrics, fingerprint, now);
                PersistResult result = persist(row, campaign.campaignName());
                switch (result.kind()) {
                    case CREATED -> {
                        created++;
                        changed.add(result.view());
                        appendCreate(context, result.view());
                    }
                    case UPDATED -> {
                        updated++;
                        changed.add(result.view());
                        appendEvidenceUpdate(context, result.view());
                    }
                    case REPLAYED -> replayed++;
                }
            }
        }
        boolean truncated = changed.size() > 20;
        List<RecommendationView> items = truncated ? List.copyOf(changed.subList(0, 20)) : List.copyOf(changed);
        return new DecisionViews.GenerateView(now, window.start(), window.end(), "Asia/Taipei", "TWD",
                campaigns.size(), created, updated, replayed, skipped, items, truncated, DecisionViews.WARNINGS);
    }

    @Transactional(readOnly = true)
    public DecisionViews.DecisionPageView list(int page, int size, RecommendationStatus status) {
        UUID account = queries.account();
        long total = queries.countByStatus(account, status);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        List<RecommendationView> content = queries.list(account, status, size, page * size).stream()
                .map(this::toView)
                .toList();
        return new DecisionViews.DecisionPageView(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public RecommendationDetailView detail(UUID recommendationUuid) {
        UUID account = queries.account();
        StoredRecommendation row = queries.findByUuid(account, recommendationUuid)
                .orElseThrow(() -> new DecisionException("DECISION_NOT_FOUND", HttpStatus.NOT_FOUND));
        return RecommendationDetailView.from(toView(row), queries.findDecision(recommendationUuid).map(this::toDecision));
    }

    @Transactional
    public RecommendationDetailView approve(UUID recommendationUuid, long expectedVersion, String requestId) {
        return decide(recommendationUuid, expectedVersion, RecommendationStatus.APPROVED, null, requestId);
    }

    @Transactional
    public RecommendationDetailView reject(UUID recommendationUuid, long expectedVersion, String reason,
            String requestId) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2000) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "reason");
        }
        return decide(recommendationUuid, expectedVersion, RecommendationStatus.REJECTED, trimmed, requestId);
    }

    private RecommendationDetailView decide(UUID recommendationUuid, long expectedVersion,
            RecommendationStatus decision, String reason, String requestId) {
        AuditOperationContext context = actor(requestId);
        UUID account = queries.account();
        queries.lockAccount(account);
        StoredRecommendation row = queries.findByUuidForUpdate(account, recommendationUuid)
                .orElseThrow(() -> new DecisionException("DECISION_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (row.version() != expectedVersion) {
            throw new DecisionException("DECISION_STALE", HttpStatus.PRECONDITION_FAILED, "If-Match");
        }
        if (row.status() != RecommendationStatus.PENDING) {
            throw new DecisionException("DECISION_ALREADY_DECIDED", HttpStatus.CONFLICT);
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        UUID decisionUuid = UUID.randomUUID();
        queries.insertDecision(decisionUuid, recommendationUuid, decision, reason,
                context.actor().type().name(), context.actor().id(), context.requestId(), row.version(), now);
        queries.markDecided(recommendationUuid, decision, row.version() + 1, now);
        List<AuditChange> decisionChanges = new ArrayList<>(List.of(
                change("recommendationDecisionUuid", null, decisionUuid.toString(), AuditValueType.UUID, 0),
                change("decision", null, decision.name(), AuditValueType.ENUM, 1),
                change("reviewerType", null, context.actor().type().name(), AuditValueType.ENUM, 2),
                change("reviewerId", null, context.actor().id(), AuditValueType.STRING, 3)));
        if (reason != null) {
            decisionChanges.add(change("reason", null, reason, AuditValueType.STRING, 4));
        }
        append(context, AuditAction.CREATE, "DECISION_RECOMMENDATION_DECISION", decisionUuid, decisionChanges);
        append(context, AuditAction.UPDATE, "DECISION_RECOMMENDATION", recommendationUuid,
                List.of(change("status", "PENDING", decision.name(), AuditValueType.ENUM, 0)));
        StoredRecommendation updated = queries.findByUuid(account, recommendationUuid).orElseThrow();
        return RecommendationDetailView.from(toView(updated),
                Optional.of(new DecisionViews.RecommendationDecisionView(decisionUuid, decision,
                        Optional.ofNullable(reason), now)));
    }

    private PersistResult persist(RecommendationRow row, String campaignName) {
        Optional<StoredRecommendation> existing = queries.findByIdentityForUpdate(
                row.account(), row.platformCampaignUuid(), row.type(), row.window());
        if (existing.isPresent()) {
            return replayOrUpdate(existing.get(), row, campaignName);
        }
        if (queries.insertRecommendation(row)) {
            return new PersistResult(PersistKind.CREATED, toView(row, campaignName, RecommendationStatus.PENDING, 0,
                    row.now(), row.now()));
        }
        StoredRecommendation raced = queries.findByIdentityForUpdate(row.account(), row.platformCampaignUuid(),
                row.type(), row.window())
                .orElseThrow(() -> new DecisionException("DECISION_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT));
        return replayOrUpdate(raced, row, campaignName);
    }

    private PersistResult replayOrUpdate(StoredRecommendation existing, RecommendationRow row, String campaignName) {
        if (existing.status() != RecommendationStatus.PENDING) {
            return new PersistResult(PersistKind.REPLAYED, toView(existing));
        }
        if (existing.fingerprint().equals(row.fingerprint())) {
            return new PersistResult(PersistKind.REPLAYED, toView(existing));
        }
        long next = existing.version() + 1;
        queries.updatePendingEvidence(existing.recommendationUuid(), row, next);
        return new PersistResult(PersistKind.UPDATED, toView(new RecommendationRow(existing.recommendationUuid(),
                row.account(), row.platformCampaignUuid(), row.campaignUuid(), row.type(), row.window(),
                row.desiredState(), row.reasonSummary(), row.riskSummary(), row.metrics(), row.fingerprint(),
                row.now()), campaignName, RecommendationStatus.PENDING, next, existing.createdAt(), row.now()));
    }

    private RecommendationView toView(StoredRecommendation row) {
        return toView(new RecommendationRow(row.recommendationUuid(), row.account(), row.platformCampaignUuid(),
                row.campaignUuid(), row.type(), row.window(), row.desiredState(), row.reasonSummary(),
                row.riskSummary(), row.metrics(), row.fingerprint(), row.updatedAt()),
                queries.campaignName(row.campaignUuid()), row.status(), row.version(), row.createdAt(),
                row.updatedAt());
    }

    private RecommendationView toView(RecommendationRow row, String campaignName, RecommendationStatus status,
            long version, Instant createdAt, Instant updatedAt) {
        Href href = href(row.type(), row.campaignUuid());
        return new RecommendationView(row.recommendationUuid(), row.platformCampaignUuid(), row.campaignUuid(),
                campaignName, row.type(), status, row.window().start(), row.window().end(), "Asia/Taipei", "TWD",
                7, 1, row.desiredState(), row.reasonSummary(), row.riskSummary(),
                DecisionQueries.evidenceOf(row.metrics()), href.path(), href.productUuid(), version, createdAt,
                updatedAt, DecisionViews.WARNINGS);
    }

    private Href href(RecommendationType type, UUID campaignUuid) {
        if (type == RecommendationType.REGENERATE_CREATIVE) {
            Optional<UUID> product = queries.singleActiveProduct(campaignUuid);
            if (product.isPresent()) {
                return new Href("/products/" + product.get() + "?tab=creative-factory", product);
            }
            return new Href("/campaigns/" + campaignUuid, Optional.empty());
        }
        return new Href("/platforms/meta", Optional.empty());
    }

    private DecisionViews.RecommendationDecisionView toDecision(DecisionQueries.DecisionRow row) {
        return new DecisionViews.RecommendationDecisionView(row.recommendationDecisionUuid(), row.decision(),
                row.reason(), row.decidedAt());
    }

    private AuditOperationContext actor(String requestId) {
        AuditOperationContext context;
        try {
            context = contexts.forCurrentActor(requestId);
        } catch (RuntimeException exception) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (context.actor().type() == AuditActorType.SYSTEM) {
            throw new DecisionException("DECISION_REQUEST_INVALID", HttpStatus.BAD_REQUEST);
        }
        return context;
    }

    private void appendCreate(AuditOperationContext context, RecommendationView view) {
        append(context, AuditAction.CREATE, "DECISION_RECOMMENDATION", view.recommendationUuid(), List.of(
                change("recommendationUuid", null, view.recommendationUuid().toString(), AuditValueType.UUID, 0),
                change("recommendationType", null, view.recommendationType().name(), AuditValueType.ENUM, 1),
                change("status", null, view.status().name(), AuditValueType.ENUM, 2)));
    }

    private void appendEvidenceUpdate(AuditOperationContext context, RecommendationView view) {
        append(context, AuditAction.UPDATE, "DECISION_RECOMMENDATION", view.recommendationUuid(), List.of(
                change("recommendationUuid", null, view.recommendationUuid().toString(), AuditValueType.UUID, 0),
                change("recommendationType", null, view.recommendationType().name(), AuditValueType.ENUM, 1),
                change("status", null, view.status().name(), AuditValueType.ENUM, 2)));
    }

    private void append(AuditOperationContext context, AuditAction action, String entityType, UUID entityUuid,
            List<AuditChange> changes) {
        audit.append(new AuditEvent(UUID.randomUUID(), context, action, entityType, entityUuid, null,
                Instant.now(clock), changes));
    }

    private AuditChange change(String field, String oldValue, String newValue, AuditValueType type, int order) {
        return new AuditChange(field, oldValue, newValue, type, order);
    }

    private enum PersistKind { CREATED, UPDATED, REPLAYED }

    private record PersistResult(PersistKind kind, RecommendationView view) {}

    private record Href(String path, Optional<UUID> productUuid) {}
}
