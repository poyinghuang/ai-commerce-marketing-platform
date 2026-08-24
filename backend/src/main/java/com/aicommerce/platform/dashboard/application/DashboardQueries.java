package com.aicommerce.platform.dashboard.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage5.enabled:false}' == 'true'")
public class DashboardQueries {
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

    public DashboardQueries(JdbcTemplate jdbc, Environment environment) {
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
            throw new DashboardException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Integer exact = jdbc.queryForObject("""
                SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='FAKE'
                  AND environment=? AND account_reference=? AND external_account_fingerprint=?
                  AND lifecycle_status='ACTIVE' AND archived_at IS NULL AND currency='TWD' AND timezone='Asia/Taipei'
                """, Integer.class, id, expectedEnvironment, reference, fingerprint);
        if (exact == null || exact != 1) {
            throw new DashboardException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return id;
    }

    Window window() {
        return jdbc.queryForObject(WINDOW_SQL, (rs, n) -> new Window(
                rs.getTimestamp(1).toInstant().truncatedTo(ChronoUnit.SECONDS),
                rs.getTimestamp(2).toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    Slice<DashboardViews.TodoItem> todos(boolean platform, int offset, int limit) {
        String sql = """
                SELECT kind, subject_uuid, product_uuid, href, title, summary, occurred_at FROM (
                  SELECT 'PRODUCT_READINESS' AS kind, p.product_uuid AS subject_uuid, p.product_uuid AS product_uuid,
                         '/products/' || p.product_uuid || '?tab=quality' AS href, p.product_name AS title,
                         w.status AS summary, w.evaluated_at AS occurred_at
                  FROM products p JOIN workflow_status w ON w.product_uuid = p.product_uuid
                  WHERE p.lifecycle_status = 'ACTIVE' AND w.status IN ('DRAFT', 'NEEDS_REVIEW')
                  UNION ALL
                  SELECT 'PENDING_REVIEW', o.generation_output_uuid, o.product_uuid,
                         '/products/' || o.product_uuid || '?tab=creative-factory', 'Pending creative review',
                         o.generation_type, o.updated_at
                  FROM ai_generation_outputs o WHERE o.review_status = 'PENDING_REVIEW'
                  UNION ALL
                  SELECT 'FAILED_GENERATION', j.generation_job_uuid, j.product_uuid,
                         '/products/' || j.product_uuid || '?tab=creative-factory', 'Generation job failed',
                         j.status, j.updated_at
                  FROM ai_generation_jobs j WHERE j.status IN ('FAILED', 'BUDGET_REJECTED')
                """ + (platform ? """
                  UNION ALL
                  SELECT 'UNKNOWN_OPERATION', o.operation_uuid, NULL,
                         '/platforms/meta', 'Unknown platform operation', o.status, o.updated_at
                  FROM platform_operations o WHERE o.platform_account_uuid = ? AND o.status = 'UNKNOWN_OUTCOME'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', c.platform_campaign_uuid, NULL,
                         '/platforms/meta', 'Platform campaign observed ERROR', c.observed_state, c.updated_at
                  FROM platform_campaigns c WHERE c.platform_account_uuid = ? AND c.observed_state = 'ERROR'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', s.platform_ad_set_uuid, NULL,
                         '/platforms/meta', 'Platform ad set observed ERROR', s.observed_state, s.updated_at
                  FROM platform_ad_sets s WHERE s.platform_account_uuid = ? AND s.observed_state = 'ERROR'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', a.platform_ad_uuid, NULL,
                         '/platforms/meta', 'Platform ad observed ERROR', a.observed_state, a.updated_at
                  FROM platform_ads a WHERE a.platform_account_uuid = ? AND a.observed_state = 'ERROR'
                """ : "") + """
                ) rows ORDER BY occurred_at DESC, subject_uuid ASC
                """;
        long total = count(sql, platform);
        List<DashboardViews.TodoItem> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) -> new DashboardViews.TodoItem(
                rs.getString("kind"),
                rs.getObject("subject_uuid", UUID.class),
                Optional.ofNullable(rs.getObject("product_uuid", UUID.class)),
                rs.getString("href"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getTimestamp("occurred_at").toInstant()), args(platform, limit, offset));
        return new Slice<>(items, total);
    }

    Slice<DashboardViews.ProductReadinessItem> products(int offset, int limit) {
        String sql = """
                SELECT p.product_uuid, p.product_name, p.lifecycle_status, w.status AS readiness_status,
                       q.final_score, (SELECT count(*) FROM quality_score_blockers b WHERE b.quality_score_uuid = q.quality_score_uuid) AS blocker_count
                FROM products p
                JOIN workflow_status w ON w.product_uuid = p.product_uuid
                JOIN quality_scores q ON q.product_uuid = p.product_uuid
                WHERE p.lifecycle_status = 'ACTIVE'
                ORDER BY CASE w.status WHEN 'DRAFT' THEN 0 WHEN 'NEEDS_REVIEW' THEN 1 ELSE 2 END,
                         q.final_score ASC, p.product_uuid ASC
                """;
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") t", Long.class);
        List<DashboardViews.ProductReadinessItem> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) -> {
            UUID id = rs.getObject("product_uuid", UUID.class);
            return new DashboardViews.ProductReadinessItem(id, rs.getString("product_name"), rs.getString("lifecycle_status"),
                    rs.getString("readiness_status"), rs.getInt("final_score"), rs.getInt("blocker_count"),
                    "/products/" + id + "?tab=quality");
        }, limit, offset);
        return new Slice<>(items, total);
    }

    Slice<ReviewRow> reviews(int offset, int limit) {
        String sql = """
                SELECT generation_output_uuid, product_uuid, generation_type, review_status, version
                FROM ai_generation_outputs WHERE review_status = 'PENDING_REVIEW'
                ORDER BY updated_at DESC, generation_output_uuid ASC
                """;
        long total = jdbc.queryForObject("SELECT count(*) FROM ai_generation_outputs WHERE review_status = 'PENDING_REVIEW'",
                Long.class);
        List<ReviewRow> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) -> new ReviewRow(
                rs.getObject("generation_output_uuid", UUID.class),
                rs.getObject("product_uuid", UUID.class),
                rs.getString("generation_type"),
                rs.getString("review_status"),
                rs.getLong("version")), limit, offset);
        return new Slice<>(items, total);
    }

    Slice<DashboardViews.CampaignPlanItem> campaigns(int offset, int limit) {
        String sql = """
                SELECT campaign_uuid, campaign_name, lifecycle_status, start_date, end_date, platform
                FROM campaign_plans WHERE lifecycle_status = 'ACTIVE'
                ORDER BY updated_at DESC, campaign_uuid ASC
                """;
        long total = jdbc.queryForObject("SELECT count(*) FROM campaign_plans WHERE lifecycle_status = 'ACTIVE'", Long.class);
        List<DashboardViews.CampaignPlanItem> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) -> {
            UUID id = rs.getObject("campaign_uuid", UUID.class);
            return new DashboardViews.CampaignPlanItem(id, rs.getString("campaign_name"), rs.getString("lifecycle_status"),
                    optionalDate(rs.getObject("start_date", LocalDate.class)),
                    optionalDate(rs.getObject("end_date", LocalDate.class)),
                    Optional.ofNullable(rs.getString("platform")),
                    "/campaigns/" + id);
        }, limit, offset);
        return new Slice<>(items, total);
    }

    Slice<DashboardViews.PlatformCampaignItem> platformCampaigns(UUID account, int offset, int limit) {
        String sql = """
                SELECT c.platform_campaign_uuid, c.campaign_uuid, p.campaign_name, c.desired_state, c.observed_state
                FROM platform_campaigns c JOIN campaign_plans p ON p.campaign_uuid = c.campaign_uuid
                WHERE c.platform_account_uuid = ? AND c.desired_state <> 'ARCHIVED'
                ORDER BY c.updated_at DESC, c.platform_campaign_uuid ASC
                """;
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM platform_campaigns WHERE platform_account_uuid = ? AND desired_state <> 'ARCHIVED'",
                Long.class, account);
        List<DashboardViews.PlatformCampaignItem> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) ->
                new DashboardViews.PlatformCampaignItem(
                        rs.getObject("platform_campaign_uuid", UUID.class),
                        rs.getObject("campaign_uuid", UUID.class),
                        rs.getString("campaign_name"),
                        rs.getString("desired_state"),
                        Optional.ofNullable(rs.getString("observed_state")),
                        "/platforms/meta"), account, limit, offset);
        return new Slice<>(items, total);
    }

    Slice<DashboardViews.AnomalyItem> anomalies(boolean platform, int offset, int limit) {
        String sql = """
                SELECT kind, subject_uuid, href, title, summary, occurred_at FROM (
                  SELECT 'FAILED_GENERATION' AS kind, j.generation_job_uuid AS subject_uuid,
                         '/products/' || j.product_uuid || '?tab=creative-factory' AS href,
                         'Generation job failed' AS title, j.status AS summary, j.updated_at AS occurred_at
                  FROM ai_generation_jobs j WHERE j.status IN ('FAILED', 'BUDGET_REJECTED')
                """ + (platform ? """
                  UNION ALL
                  SELECT 'UNKNOWN_OPERATION', o.operation_uuid, '/platforms/meta',
                         'Unknown platform operation', o.status, o.updated_at
                  FROM platform_operations o WHERE o.platform_account_uuid = ? AND o.status = 'UNKNOWN_OUTCOME'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', c.platform_campaign_uuid, '/platforms/meta',
                         'Platform campaign observed ERROR', c.observed_state, c.updated_at
                  FROM platform_campaigns c WHERE c.platform_account_uuid = ? AND c.observed_state = 'ERROR'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', s.platform_ad_set_uuid, '/platforms/meta',
                         'Platform ad set observed ERROR', s.observed_state, s.updated_at
                  FROM platform_ad_sets s WHERE s.platform_account_uuid = ? AND s.observed_state = 'ERROR'
                  UNION ALL
                  SELECT 'PLATFORM_ERROR', a.platform_ad_uuid, '/platforms/meta',
                         'Platform ad observed ERROR', a.observed_state, a.updated_at
                  FROM platform_ads a WHERE a.platform_account_uuid = ? AND a.observed_state = 'ERROR'
                """ : "") + """
                ) rows ORDER BY occurred_at DESC, subject_uuid ASC
                """;
        long total = count(sql, platform);
        List<DashboardViews.AnomalyItem> items = jdbc.query(sql + " LIMIT ? OFFSET ?", (rs, n) -> new DashboardViews.AnomalyItem(
                rs.getString("kind"),
                rs.getObject("subject_uuid", UUID.class),
                rs.getString("href"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getTimestamp("occurred_at").toInstant()), args(platform, limit, offset));
        return new Slice<>(items, total);
    }

    DashboardViews.KpiOverview kpis(UUID account) {
        Window window = window();
        Integer eligible = jdbc.queryForObject(
                "SELECT count(*) FROM platform_campaigns WHERE platform_account_uuid = ? AND desired_state <> 'ARCHIVED'",
                Integer.class, account);
        int eligibleCount = eligible == null ? 0 : eligible;
        List<MetricRow> present = jdbc.query("""
                SELECT DISTINCT ON (s.platform_campaign_uuid)
                       s.impressions, s.reach, s.clicks, s.conversions, s.spend, s.revenue
                FROM platform_metric_snapshots s
                JOIN platform_campaigns c ON c.platform_campaign_uuid = s.platform_campaign_uuid
                  AND c.platform_account_uuid = s.platform_account_uuid
                WHERE s.platform_account_uuid = ? AND s.entity_type = 'CAMPAIGN'
                  AND s.platform_ad_set_uuid IS NULL AND s.platform_ad_uuid IS NULL
                  AND c.desired_state <> 'ARCHIVED'
                  AND s.window_start = ? AND s.window_end = ?
                  AND s.timezone = 'Asia/Taipei' AND s.attribution_click_days = 7 AND s.attribution_view_days = 1
                  AND s.currency = 'TWD'
                ORDER BY s.platform_campaign_uuid, s.revision_number DESC
                """, (rs, n) -> new MetricRow(
                optionalLong(rs, "impressions"), optionalLong(rs, "reach"), optionalLong(rs, "clicks"),
                optionalLong(rs, "conversions"), optionalMoney(rs, "spend"), optionalMoney(rs, "revenue")),
                account, Timestamp.from(window.start()), Timestamp.from(window.end()));
        int presentCount = present.size();
        boolean incomplete = presentCount < eligibleCount
                || present.stream().anyMatch(row -> row.impressions.isEmpty() || row.reach.isEmpty()
                        || row.clicks.isEmpty() || row.conversions.isEmpty() || row.spend.isEmpty() || row.revenue.isEmpty());
        Optional<Long> impressions = sumLong(present.stream().map(row -> row.impressions).toList());
        Optional<Long> reach = sumLong(present.stream().map(row -> row.reach).toList());
        Optional<Long> clicks = sumLong(present.stream().map(row -> row.clicks).toList());
        Optional<Long> conversions = sumLong(present.stream().map(row -> row.conversions).toList());
        Optional<BigDecimal> spend = sumMoney(present.stream().map(row -> row.spend).toList());
        Optional<BigDecimal> revenue = sumMoney(present.stream().map(row -> row.revenue).toList());
        return new DashboardViews.KpiOverview(true,
                Optional.of(window.start()), Optional.of(window.end()), Optional.of("Asia/Taipei"), Optional.of("TWD"),
                Optional.of(7), Optional.of(1), Optional.of(eligibleCount), Optional.of(presentCount), Optional.of(incomplete),
                impressions, reach, clicks, conversions, spend.map(DashboardQueries::money), revenue.map(DashboardQueries::money),
                ratio(clicks, impressions), moneyRatio(spend, clicks), cpm(spend, impressions), moneyRatio(spend, conversions),
                ratio(conversions, clicks), moneyOverMoney(revenue, spend));
    }

    private long count(String inner, boolean platform) {
        Object[] accountArgs = platform ? accountArgs() : new Object[0];
        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + inner + ") t", Long.class, accountArgs);
        return total == null ? 0 : total;
    }

    private Object[] args(boolean platform, int limit, int offset) {
        List<Object> values = new ArrayList<>();
        if (platform) {
            values.addAll(Arrays.asList(accountArgs()));
        }
        values.add(limit);
        values.add(offset);
        return values.toArray();
    }

    private Object[] accountArgs() {
        UUID account = account();
        return new Object[] {account, account, account, account};
    }

    private static Optional<String> optionalDate(LocalDate value) {
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    private static Optional<Long> optionalLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<BigDecimal> optionalMoney(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Long> sumLong(List<Optional<Long>> values) {
        if (values.isEmpty() || values.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(values.stream().mapToLong(Optional::get).sum());
    }

    private static Optional<BigDecimal> sumMoney(List<Optional<BigDecimal>> values) {
        if (values.isEmpty() || values.stream().anyMatch(Optional::isEmpty)) {
            return Optional.empty();
        }
        return Optional.of(values.stream().map(Optional::get).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static Optional<String> ratio(Optional<Long> numerator, Optional<Long> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get() == 0) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(numerator.get())
                .divide(BigDecimal.valueOf(denominator.get()), 6, RoundingMode.HALF_UP).toPlainString());
    }

    private static Optional<String> moneyRatio(Optional<BigDecimal> numerator, Optional<Long> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get() == 0) return Optional.empty();
        return Optional.of(numerator.get().divide(BigDecimal.valueOf(denominator.get()), 6, RoundingMode.HALF_UP)
                .toPlainString());
    }

    private static Optional<String> moneyOverMoney(Optional<BigDecimal> numerator, Optional<BigDecimal> denominator) {
        if (numerator.isEmpty() || denominator.isEmpty() || denominator.get().signum() == 0) return Optional.empty();
        return Optional.of(numerator.get().divide(denominator.get(), 6, RoundingMode.HALF_UP).toPlainString());
    }

    private static Optional<String> cpm(Optional<BigDecimal> spend, Optional<Long> impressions) {
        if (spend.isEmpty() || impressions.isEmpty() || impressions.get() == 0) return Optional.empty();
        return Optional.of(spend.get().multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(impressions.get()), 6, RoundingMode.HALF_UP).toPlainString());
    }

    private static String money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    record Window(Instant start, Instant end) {}
    record Slice<T>(List<T> items, long total) {}
    record ReviewRow(UUID outputUuid, UUID productUuid, String generationType, String reviewStatus, long version) {}
    private record MetricRow(Optional<Long> impressions, Optional<Long> reach, Optional<Long> clicks,
            Optional<Long> conversions, Optional<BigDecimal> spend, Optional<BigDecimal> revenue) {}
}
