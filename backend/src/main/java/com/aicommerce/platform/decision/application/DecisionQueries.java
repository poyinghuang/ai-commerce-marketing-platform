package com.aicommerce.platform.decision.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.decision.application.DecisionViews.RecommendationStatus;
import com.aicommerce.platform.decision.application.DecisionViews.RecommendationType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage6.enabled:false}' == 'true'")
public class DecisionQueries {
    static final String WINDOW_SQL = """
            SELECT
              ((platform_taipei_business_date(statement_timestamp()) - 1)
                AT TIME ZONE 'Asia/Taipei') AS window_start,
              (platform_taipei_business_date(statement_timestamp())
                AT TIME ZONE 'Asia/Taipei') AS window_end
            """;
    private static final UUID LOCAL_ACCOUNT = UUID.fromString("00000000-0000-4000-8000-00000000004b");
    private static final UUID TEST_ACCOUNT = UUID.fromString("00000000-0000-4000-8000-00000000005b");
    private static final String LOCAL_FINGERPRINT = "4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36";
    private static final String TEST_FINGERPRINT = "9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2";

    private final JdbcTemplate jdbc;
    private final Environment environment;

    public DecisionQueries(JdbcTemplate jdbc, Environment environment) {
        this.jdbc = jdbc;
        this.environment = environment;
    }

    UUID account() {
        boolean test = Arrays.asList(environment.getActiveProfiles()).contains("test");
        UUID id = test ? TEST_ACCOUNT : LOCAL_ACCOUNT;
        String reference = test ? "stage4b-test" : "stage4b-local";
        String expectedEnvironment = test ? "TEST" : "LOCAL";
        String fingerprint = test ? TEST_FINGERPRINT : LOCAL_FINGERPRINT;
        List<UUID> candidates = jdbc.query(
                "SELECT platform_account_uuid FROM platform_accounts WHERE provider_key='FAKE' AND account_reference=?",
                (rs, n) -> rs.getObject(1, UUID.class), reference);
        if (candidates.size() != 1 || !id.equals(candidates.getFirst())) {
            throw new DecisionException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Integer exact = jdbc.queryForObject("""
                SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='FAKE'
                  AND environment=? AND account_reference=? AND external_account_fingerprint=?
                  AND lifecycle_status='ACTIVE' AND archived_at IS NULL AND currency='TWD' AND timezone='Asia/Taipei'
                """, Integer.class, id, expectedEnvironment, reference, fingerprint);
        if (exact == null || exact != 1) {
            throw new DecisionException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return id;
    }

    void lockAccount(UUID account) {
        jdbc.query("SELECT 1 FROM platform_accounts WHERE platform_account_uuid=? FOR UPDATE",
                (rs, n) -> 1, account);
    }

    Window window() {
        return jdbc.queryForObject(WINDOW_SQL, (rs, n) -> new Window(
                rs.getTimestamp(1).toInstant().truncatedTo(ChronoUnit.SECONDS),
                rs.getTimestamp(2).toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    List<CampaignRow> eligibleCampaigns(UUID account) {
        return jdbc.query("""
                SELECT c.platform_campaign_uuid, c.campaign_uuid, c.desired_state, p.campaign_name
                FROM platform_campaigns c JOIN campaign_plans p ON p.campaign_uuid = c.campaign_uuid
                WHERE c.platform_account_uuid = ? AND c.desired_state <> 'ARCHIVED'
                ORDER BY c.platform_campaign_uuid ASC
                """, (rs, n) -> new CampaignRow(
                rs.getObject("platform_campaign_uuid", UUID.class),
                rs.getObject("campaign_uuid", UUID.class),
                rs.getString("desired_state"),
                rs.getString("campaign_name")), account);
    }

    Optional<SnapshotRow> latestCampaignSnapshot(UUID account, UUID platformCampaign, Window window) {
        List<SnapshotRow> rows = jdbc.query("""
                SELECT impressions, reach, clicks, conversions, spend, revenue, source_fingerprint
                FROM platform_metric_snapshots
                WHERE platform_account_uuid = ? AND entity_type = 'CAMPAIGN'
                  AND platform_campaign_uuid = ? AND platform_ad_set_uuid IS NULL AND platform_ad_uuid IS NULL
                  AND window_start = ? AND window_end = ?
                  AND timezone = 'Asia/Taipei' AND attribution_click_days = 7 AND attribution_view_days = 1
                  AND currency = 'TWD'
                ORDER BY revision_number DESC
                LIMIT 1
                """, (rs, n) -> new SnapshotRow(
                optionalLong(rs, "impressions"), optionalLong(rs, "reach"), optionalLong(rs, "clicks"),
                optionalLong(rs, "conversions"), optionalMoney(rs, "spend"), optionalMoney(rs, "revenue"),
                rs.getString("source_fingerprint")),
                account, platformCampaign, Timestamp.from(window.start()), Timestamp.from(window.end()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    String campaignName(UUID campaignUuid) {
        return jdbc.queryForObject("SELECT campaign_name FROM campaign_plans WHERE campaign_uuid=?",
                String.class, campaignUuid);
    }

    Optional<UUID> singleActiveProduct(UUID campaignUuid) {
        List<UUID> products = jdbc.query("""
                SELECT product_uuid FROM campaign_products
                WHERE campaign_uuid = ? AND lifecycle_status = 'ACTIVE'
                ORDER BY priority NULLS LAST, product_uuid ASC
                """, (rs, n) -> rs.getObject(1, UUID.class), campaignUuid);
        return products.size() == 1 ? Optional.of(products.getFirst()) : Optional.empty();
    }

    boolean insertRecommendation(RecommendationRow row) {
        Boolean created = jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            Savepoint save = connection.setSavepoint("decision_insert");
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO decision_recommendations(
                        recommendation_uuid, platform_account_uuid, platform_campaign_uuid, campaign_uuid,
                        recommendation_type, status, window_start, window_end, timezone,
                        attribution_click_days, attribution_view_days, currency, desired_state,
                        reason_summary, risk_summary, impressions, reach, clicks, conversions, spend, revenue,
                        ctr, cpc, cpm, cpa, cvr, roas, rule_set_key, evidence_fingerprint, version, created_at, updated_at)
                    VALUES (?,?,?,?,?,'PENDING',?,?, 'Asia/Taipei', 7, 1, 'TWD', ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'RULE_SET_V1', ?, 0, ?, ?)
                    """)) {
                int index = 1;
                statement.setObject(index++, row.recommendationUuid());
                statement.setObject(index++, row.account());
                statement.setObject(index++, row.platformCampaignUuid());
                statement.setObject(index++, row.campaignUuid());
                statement.setString(index++, row.type().name());
                statement.setTimestamp(index++, Timestamp.from(row.window().start()));
                statement.setTimestamp(index++, Timestamp.from(row.window().end()));
                statement.setString(index++, row.desiredState());
                statement.setString(index++, row.reasonSummary());
                statement.setString(index++, row.riskSummary());
                statement.setObject(index++, row.metrics().impressions().orElse(null));
                statement.setObject(index++, row.metrics().reach().orElse(null));
                statement.setObject(index++, row.metrics().clicks().orElse(null));
                statement.setObject(index++, row.metrics().conversions().orElse(null));
                statement.setObject(index++, row.metrics().spend().orElse(null));
                statement.setObject(index++, row.metrics().revenue().orElse(null));
                statement.setObject(index++, row.metrics().ctr().orElse(null));
                statement.setObject(index++, row.metrics().cpc().orElse(null));
                statement.setObject(index++, row.metrics().cpm().orElse(null));
                statement.setObject(index++, row.metrics().cpa().orElse(null));
                statement.setObject(index++, row.metrics().cvr().orElse(null));
                statement.setObject(index++, row.metrics().roas().orElse(null));
                statement.setString(index++, row.fingerprint());
                statement.setTimestamp(index++, Timestamp.from(row.now()));
                statement.setTimestamp(index, Timestamp.from(row.now()));
                statement.executeUpdate();
                connection.releaseSavepoint(save);
                return true;
            } catch (SQLException exception) {
                if (!"23505".equals(exception.getSQLState())) {
                    throw exception;
                }
                connection.rollback(save);
                return false;
            }
        });
        return Boolean.TRUE.equals(created);
    }

    Optional<StoredRecommendation> findByIdentityForUpdate(UUID account, UUID platformCampaign,
            RecommendationType type, Window window) {
        List<StoredRecommendation> rows = jdbc.query("""
                SELECT * FROM decision_recommendations
                WHERE platform_account_uuid = ? AND platform_campaign_uuid = ? AND recommendation_type = ?
                  AND window_start = ? AND window_end = ? AND timezone = 'Asia/Taipei'
                  AND attribution_click_days = 7 AND attribution_view_days = 1 AND currency = 'TWD'
                FOR UPDATE
                """, DecisionQueries::mapStored, account, platformCampaign, type.name(),
                Timestamp.from(window.start()), Timestamp.from(window.end()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    void updatePendingEvidence(UUID recommendationUuid, RecommendationRow row, long newVersion) {
        jdbc.update("""
                UPDATE decision_recommendations SET desired_state=?, reason_summary=?, risk_summary=?,
                    impressions=?, reach=?, clicks=?, conversions=?, spend=?, revenue=?,
                    ctr=?, cpc=?, cpm=?, cpa=?, cvr=?, roas=?, evidence_fingerprint=?, version=?, updated_at=?
                WHERE recommendation_uuid=? AND status='PENDING'
                """, row.desiredState(), row.reasonSummary(), row.riskSummary(),
                row.metrics().impressions().orElse(null), row.metrics().reach().orElse(null),
                row.metrics().clicks().orElse(null), row.metrics().conversions().orElse(null),
                row.metrics().spend().orElse(null), row.metrics().revenue().orElse(null),
                row.metrics().ctr().orElse(null), row.metrics().cpc().orElse(null),
                row.metrics().cpm().orElse(null), row.metrics().cpa().orElse(null),
                row.metrics().cvr().orElse(null), row.metrics().roas().orElse(null),
                row.fingerprint(), newVersion, Timestamp.from(row.now()), recommendationUuid);
    }

    Optional<StoredRecommendation> findByUuidForUpdate(UUID account, UUID recommendationUuid) {
        List<StoredRecommendation> rows = jdbc.query("""
                SELECT * FROM decision_recommendations
                WHERE recommendation_uuid=? AND platform_account_uuid=?
                FOR UPDATE
                """, DecisionQueries::mapStored, recommendationUuid, account);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    Optional<StoredRecommendation> findByUuid(UUID account, UUID recommendationUuid) {
        List<StoredRecommendation> rows = jdbc.query("""
                SELECT * FROM decision_recommendations
                WHERE recommendation_uuid=? AND platform_account_uuid=?
                """, DecisionQueries::mapStored, recommendationUuid, account);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    void insertDecision(UUID decisionUuid, UUID recommendationUuid, RecommendationStatus decision, String reason,
            String reviewerType, String reviewerId, String requestId, long reviewedVersion, Instant decidedAt) {
        jdbc.update("""
                INSERT INTO decision_recommendation_decisions(
                    recommendation_decision_uuid, recommendation_uuid, decision, reason, reviewer_type, reviewer_id,
                    request_id, reviewed_recommendation_version, decided_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, decisionUuid, recommendationUuid, decision.name(), reason, reviewerType, reviewerId,
                requestId, reviewedVersion, Timestamp.from(decidedAt));
    }

    void markDecided(UUID recommendationUuid, RecommendationStatus status, long newVersion, Instant updatedAt) {
        jdbc.update("""
                UPDATE decision_recommendations SET status=?, version=?, updated_at=?
                WHERE recommendation_uuid=? AND status='PENDING'
                """, status.name(), newVersion, Timestamp.from(updatedAt), recommendationUuid);
    }

    Optional<DecisionRow> findDecision(UUID recommendationUuid) {
        List<DecisionRow> rows = jdbc.query("""
                SELECT recommendation_decision_uuid, decision, reason, decided_at
                FROM decision_recommendation_decisions WHERE recommendation_uuid=?
                """, (rs, n) -> new DecisionRow(
                rs.getObject("recommendation_decision_uuid", UUID.class),
                RecommendationStatus.valueOf(rs.getString("decision")),
                Optional.ofNullable(rs.getString("reason")),
                rs.getTimestamp("decided_at").toInstant()), recommendationUuid);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    long countByStatus(UUID account, RecommendationStatus status) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM decision_recommendations
                WHERE platform_account_uuid=? AND status=?
                """, Long.class, account, status.name());
        return total == null ? 0 : total;
    }

    List<StoredRecommendation> list(UUID account, RecommendationStatus status, int limit, int offset) {
        return jdbc.query("""
                SELECT * FROM decision_recommendations
                WHERE platform_account_uuid=? AND status=?
                ORDER BY updated_at DESC, recommendation_uuid DESC
                LIMIT ? OFFSET ?
                """, DecisionQueries::mapStored, account, status.name(), limit, offset);
    }

    static RuleSetV1.Metrics metricsFrom(SnapshotRow snapshot) {
        Optional<BigDecimal> ctr = ratio(snapshot.clicks(), snapshot.impressions());
        Optional<BigDecimal> cpc = moneyRatio(snapshot.spend(), snapshot.clicks());
        Optional<BigDecimal> cpm = cpm(snapshot.spend(), snapshot.impressions());
        Optional<BigDecimal> cpa = moneyRatio(snapshot.spend(), snapshot.conversions());
        Optional<BigDecimal> cvr = ratio(snapshot.conversions(), snapshot.clicks());
        Optional<BigDecimal> roas = moneyOverMoney(snapshot.revenue(), snapshot.spend());
        return new RuleSetV1.Metrics(snapshot.impressions(), snapshot.reach(), snapshot.clicks(), snapshot.conversions(),
                snapshot.spend(), snapshot.revenue(), ctr, cpc, cpm, cpa, cvr, roas);
    }

    static DecisionViews.RecommendationEvidence evidenceOf(RuleSetV1.Metrics metrics) {
        return new DecisionViews.RecommendationEvidence(
                metrics.impressions(), metrics.reach(), metrics.clicks(), metrics.conversions(),
                metrics.spend().map(DecisionQueries::money), metrics.revenue().map(DecisionQueries::money),
                metrics.ctr().map(DecisionQueries::derived), metrics.cpc().map(DecisionQueries::derived),
                metrics.cpm().map(DecisionQueries::derived), metrics.cpa().map(DecisionQueries::derived),
                metrics.cvr().map(DecisionQueries::derived), metrics.roas().map(DecisionQueries::derived));
    }

    static String money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    static String derived(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static Optional<BigDecimal> ratio(Optional<Long> numerator, Optional<Long> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get() == 0) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(numerator.get())
                .divide(BigDecimal.valueOf(denominator.get()), 6, RoundingMode.HALF_UP));
    }

    private static Optional<BigDecimal> moneyRatio(Optional<BigDecimal> numerator, Optional<Long> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get() == 0) return Optional.empty();
        return Optional.of(numerator.get().divide(BigDecimal.valueOf(denominator.get()), 6, RoundingMode.HALF_UP));
    }

    private static Optional<BigDecimal> moneyOverMoney(Optional<BigDecimal> numerator, Optional<BigDecimal> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get().signum() == 0) return Optional.empty();
        return Optional.of(numerator.get().divide(denominator.get(), 6, RoundingMode.HALF_UP));
    }

    private static Optional<BigDecimal> cpm(Optional<BigDecimal> spend, Optional<Long> impressions) {
        if (spend.isEmpty() || impressions.isEmpty() || impressions.get() == 0) return Optional.empty();
        return Optional.of(spend.get().multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(impressions.get()), 6, RoundingMode.HALF_UP));
    }

    private static StoredRecommendation mapStored(ResultSet rs, int n) throws SQLException {
        RuleSetV1.Metrics metrics = new RuleSetV1.Metrics(
                optionalLong(rs, "impressions"), optionalLong(rs, "reach"), optionalLong(rs, "clicks"),
                optionalLong(rs, "conversions"), optionalMoney(rs, "spend"), optionalMoney(rs, "revenue"),
                optionalMoney(rs, "ctr"), optionalMoney(rs, "cpc"), optionalMoney(rs, "cpm"),
                optionalMoney(rs, "cpa"), optionalMoney(rs, "cvr"), optionalMoney(rs, "roas"));
        return new StoredRecommendation(
                rs.getObject("recommendation_uuid", UUID.class),
                rs.getObject("platform_account_uuid", UUID.class),
                rs.getObject("platform_campaign_uuid", UUID.class),
                rs.getObject("campaign_uuid", UUID.class),
                RecommendationType.valueOf(rs.getString("recommendation_type")),
                RecommendationStatus.valueOf(rs.getString("status")),
                new Window(rs.getTimestamp("window_start").toInstant(), rs.getTimestamp("window_end").toInstant()),
                rs.getString("desired_state"),
                rs.getString("reason_summary"),
                rs.getString("risk_summary"),
                metrics,
                rs.getString("evidence_fingerprint"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Optional<Long> optionalLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<BigDecimal> optionalMoney(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    record Window(Instant start, Instant end) {}

    record CampaignRow(UUID platformCampaignUuid, UUID campaignUuid, String desiredState, String campaignName) {}

    record SnapshotRow(Optional<Long> impressions, Optional<Long> reach, Optional<Long> clicks,
            Optional<Long> conversions, Optional<BigDecimal> spend, Optional<BigDecimal> revenue,
            String sourceFingerprint) {}

    record RecommendationRow(UUID recommendationUuid, UUID account, UUID platformCampaignUuid, UUID campaignUuid,
            RecommendationType type, Window window, String desiredState, String reasonSummary, String riskSummary,
            RuleSetV1.Metrics metrics, String fingerprint, Instant now) {}

    record StoredRecommendation(UUID recommendationUuid, UUID account, UUID platformCampaignUuid, UUID campaignUuid,
            RecommendationType type, RecommendationStatus status, Window window, String desiredState,
            String reasonSummary, String riskSummary, RuleSetV1.Metrics metrics, String fingerprint, long version,
            Instant createdAt, Instant updatedAt) {}

    record DecisionRow(UUID recommendationDecisionUuid, RecommendationStatus decision, Optional<String> reason,
            Instant decidedAt) {}
}
