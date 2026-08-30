package com.aicommerce.platform.delivery.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditAction;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEvent;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditEventKind;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditSubjectType;
import com.aicommerce.platform.delivery.application.audit.PlatformAuditWriter;
import com.aicommerce.platform.delivery.application.port.PlatformDeliveryReadPort;
import com.aicommerce.platform.delivery.application.port.PlatformMetricsReadPort;
import com.aicommerce.platform.delivery.domain.FreshnessStatus;
import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("(local | test) & !production")
public class Stage4DTransactions {
    private static final String WINDOW_SQL = """
            SELECT
              ((platform_taipei_business_date(statement_timestamp()) - 1)
                AT TIME ZONE 'Asia/Taipei') AS window_start,
              (platform_taipei_business_date(statement_timestamp())
                AT TIME ZONE 'Asia/Taipei') AS window_end
            """;

    private final JdbcTemplate jdbc;
    private final Stage4BTransactions stage4b;
    private final PlatformAuditWriter audit;
    private final AuditOperationContextFactory contexts;
    private final Environment environment;
    private final boolean liveInsights;

    public Stage4DTransactions(JdbcTemplate jdbc, Stage4BTransactions stage4b, PlatformAuditWriter audit,
            AuditOperationContextFactory contexts, Environment environment,
            @Value("${platform.stage8.insights.live:false}") boolean liveInsights) {
        this.jdbc = jdbc;
        this.stage4b = stage4b;
        this.audit = audit;
        this.contexts = contexts;
        this.environment = environment;
        this.liveInsights = liveInsights;
    }

    @Transactional(readOnly = true)
    public Stage4DViews.DeliveryView delivery(PlatformEntityType type, UUID entityUuid) {
        return view(load(account(), type, entityUuid, false));
    }

    @Transactional(readOnly = true)
    public Stage4DViews.DeliveryPreview previewDelivery(PlatformEntityType type, UUID entityUuid) {
        EntityRow row = requireRefreshable(account(), type, entityUuid, false);
        return new Stage4DViews.DeliveryPreview(type, entityUuid, row.desired(), row.observed(), true,
                Stage4DViews.WARNINGS, true);
    }

    @Transactional(readOnly = true)
    public Stage4DViews.MetricsPreview previewMetrics(PlatformEntityType type, UUID entityUuid) {
        requireRefreshable(account(), type, entityUuid, false);
        Window window = window();
        return new Stage4DViews.MetricsPreview(type, entityUuid, window.start(), window.end(), "Asia/Taipei", 7, 1,
                "TWD", true, Stage4DViews.WARNINGS, true);
    }

    @Transactional(readOnly = true)
    public Stage4DViews.MetricsView metrics(PlatformEntityType type, UUID entityUuid, Optional<Instant> asOf) {
        load(account(), type, entityUuid, false);
        Window window = window();
        return select(account(), type, entityUuid, window, asOf);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Eligibility lockDelivery(PlatformEntityType type, UUID entityUuid) {
        UUID account = account();
        EntityRow row = requireRefreshable(account, type, entityUuid, true);
        return new Eligibility(account, row, window());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Eligibility lockMetrics(PlatformEntityType type, UUID entityUuid) {
        return lockDelivery(type, entityUuid);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Stage4DViews.DeliveryView persistDelivery(Eligibility eligibility,
            PlatformDeliveryReadPort.DeliveryObservation observation, String requestId) {
        UUID account = account();
        EntityRow locked = requireRefreshable(account, eligibility.row().type(), eligibility.row().uuid(), true);
        if (!locked.uuid().equals(eligibility.row().uuid()) || locked.desired() != eligibility.row().desired()) {
            throw notFound();
        }
        Optional<PlatformObservedState> next = Optional.of(observation.observedState());
        if (Objects.equals(locked.observed(), next)) {
            return view(locked);
        }
        String table = table(locked.type());
        String column = column(locked.type());
        int updated = jdbc.update("UPDATE " + table + " SET observed_state=?, updated_at=statement_timestamp(), version=version+1 WHERE "
                        + column + "=? AND platform_account_uuid=? AND version=?",
                observation.observedState().name(), locked.uuid(), account, locked.version());
        if (updated != 1) throw conflict();
        EntityRow after = load(account, locked.type(), locked.uuid(), false);
        AuditOperationContext context = contexts.forCurrentActor(requestId);
        audit.write(observationEvent(locked, after), context);
        return view(after);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Stage4DViews.MetricsView persistMetrics(Eligibility eligibility,
            PlatformMetricsReadPort.MetricObservation observation, Instant now) {
        if (observation.fetchedAt().isAfter(now)) throw contract();
        UUID account = account();
        EntityRow locked = requireRefreshable(account, eligibility.row().type(), eligibility.row().uuid(), true);
        Window window = window();
        String fingerprint = Stage4DMetricFingerprint.hash(locked.type(), locked.uuid(), window.start(), window.end(),
                observation.impressions(), observation.reach(), observation.clicks(), observation.conversions(),
                observation.spend(), observation.revenue(), observation.freshnessStatus());
        Optional<Stage4DViews.MetricsView> replay = matching(account, locked.type(), locked.uuid(), window, fingerprint);
        if (replay.isPresent()) return replay.get();
        Timestamp latestFetched = jdbc.queryForObject("SELECT MAX(fetched_at) FROM platform_metric_snapshots WHERE "
                        + "platform_account_uuid=? AND entity_type=? AND " + entityPredicate(locked.type())
                        + " AND window_start=? AND window_end=? AND timezone='Asia/Taipei' AND attribution_click_days=7 "
                        + "AND attribution_view_days=1 AND currency='TWD'",
                Timestamp.class, params(account, locked.type(), locked.uuid(), window));
        if (latestFetched != null && !observation.fetchedAt().isAfter(latestFetched.toInstant())) {
            throw contract();
        }
        Integer nextRevision = jdbc.queryForObject("SELECT COALESCE(MAX(revision_number),0)+1 FROM platform_metric_snapshots WHERE "
                        + "platform_account_uuid=? AND entity_type=? AND " + entityPredicate(locked.type())
                        + " AND window_start=? AND window_end=? AND timezone='Asia/Taipei' AND attribution_click_days=7 "
                        + "AND attribution_view_days=1 AND currency='TWD'",
                Integer.class, params(account, locked.type(), locked.uuid(), window));
        try {
            jdbc.update("""
                    INSERT INTO platform_metric_snapshots(
                      metric_snapshot_uuid, platform_account_uuid, entity_type, platform_campaign_uuid, platform_ad_set_uuid,
                      platform_ad_uuid, window_start, window_end, timezone, attribution_click_days, attribution_view_days,
                      currency, impressions, reach, clicks, conversions, spend, revenue, revision_number, fetched_at,
                      freshness_status, source_fingerprint)
                    VALUES (?,?,?,?,?,?,?,?, 'Asia/Taipei', 7, 1, 'TWD', ?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(), account, locked.type().name(),
                    locked.type() == PlatformEntityType.CAMPAIGN ? locked.uuid() : null,
                    locked.type() == PlatformEntityType.AD_SET ? locked.uuid() : null,
                    locked.type() == PlatformEntityType.AD ? locked.uuid() : null,
                    Timestamp.from(window.start()), Timestamp.from(window.end()),
                    observation.impressions().orElse(null), observation.reach().orElse(null),
                    observation.clicks().orElse(null), observation.conversions().orElse(null),
                    observation.spend().orElse(null), observation.revenue().orElse(null),
                    nextRevision, Timestamp.from(observation.fetchedAt().truncatedTo(ChronoUnit.SECONDS)),
                    observation.freshnessStatus().name(), fingerprint);
        } catch (DataAccessException exception) {
            String state = sqlState(exception);
            if ("23505".equals(state)) {
                return matching(account, locked.type(), locked.uuid(), window, fingerprint).orElseThrow(Stage4DTransactions::contract);
            }
            if ("23514".equals(state)) throw contract();
            if ("40001".equals(state) || "40P01".equals(state)) throw conflict();
            throw exception;
        }
        return select(account, locked.type(), locked.uuid(), window, Optional.empty());
    }

    PlatformDeliveryReadPort.DeliveryReadCommand deliveryCommand(Eligibility eligibility) {
        EntityRow row = eligibility.row();
        return new PlatformDeliveryReadPort.DeliveryReadCommand(
                eligibility.account(), row.type(), row.uuid(), row.externalId(), row.desired());
    }

    PlatformMetricsReadPort.MetricReadCommand metricsCommand(Eligibility eligibility) {
        EntityRow row = eligibility.row();
        Window window = eligibility.window();
        return new PlatformMetricsReadPort.MetricReadCommand(
                eligibility.account(), row.type(), row.uuid(), row.externalId(), window.start(), window.end(),
                "Asia/Taipei", 7, 1, "TWD");
    }

    private EntityRow requireRefreshable(UUID account, PlatformEntityType type, UUID entityUuid, boolean lock) {
        EntityRow row = load(account, type, entityUuid, lock);
        if (row.desired() == PlatformDesiredState.ARCHIVED) {
            throw new Stage4BException("PLATFORM_ENTITY_ARCHIVED", HttpStatus.CONFLICT);
        }
        if (row.externalId() == null || row.externalId().isBlank()) {
            throw new Stage4BException("PLATFORM_DELIVERY_NOT_SYNCABLE", HttpStatus.CONFLICT);
        }
        return row;
    }

    private EntityRow load(UUID account, PlatformEntityType type, UUID entityUuid, boolean lock) {
        lockAccount(account, lock);
        if (lock) {
            List<Integer> locked = jdbc.query("SELECT 1 FROM " + table(type) + " WHERE " + column(type)
                    + "=? AND platform_account_uuid=? FOR UPDATE", (rs, n) -> 1, entityUuid, account);
            if (locked.size() != 1) throw notFound();
        }
        List<EntityRow> rows = jdbc.query("SELECT " + column(type) + ", desired_state, observed_state, external_id, updated_at, version FROM "
                        + table(type) + " WHERE " + column(type) + "=? AND platform_account_uuid=?",
                (rs, n) -> new EntityRow(type, rs.getObject(1, UUID.class),
                        PlatformDesiredState.valueOf(rs.getString(2)),
                        Optional.ofNullable(rs.getString(3)).map(PlatformObservedState::valueOf),
                        rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getLong(6)),
                entityUuid, account);
        if (rows.size() != 1) throw notFound();
        return rows.getFirst();
    }

    private void lockAccount(UUID account, boolean lock) {
        if (!lock) return;
        List<Integer> locked = jdbc.query(
                "SELECT 1 FROM platform_accounts WHERE platform_account_uuid=? FOR UPDATE", (rs, n) -> 1, account);
        if (locked.size() != 1) {
            throw new Stage4BException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Window window() {
        return jdbc.queryForObject(WINDOW_SQL, (rs, n) -> new Window(
                rs.getTimestamp(1).toInstant().truncatedTo(ChronoUnit.SECONDS),
                rs.getTimestamp(2).toInstant().truncatedTo(ChronoUnit.SECONDS)));
    }

    private Stage4DViews.MetricsView select(UUID account, PlatformEntityType type, UUID entityUuid, Window window,
            Optional<Instant> asOf) {
        String sql = "SELECT revision_number, fetched_at, freshness_status, impressions, reach, clicks, conversions, spend, revenue "
                + "FROM platform_metric_snapshots WHERE platform_account_uuid=? AND entity_type=? AND "
                + entityPredicate(type) + " AND window_start=? AND window_end=? AND timezone='Asia/Taipei' "
                + "AND attribution_click_days=7 AND attribution_view_days=1 AND currency='TWD' "
                + (asOf.isPresent() ? "AND fetched_at<=? " : "")
                + "ORDER BY revision_number DESC LIMIT 1";
        Object[] args = asOf.isPresent()
                ? append(params(account, type, entityUuid, window), Timestamp.from(asOf.get()))
                : params(account, type, entityUuid, window);
        List<Stage4DViews.MetricsView> rows = jdbc.query(sql, (rs, n) -> metricsView(type, entityUuid, window, true,
                FreshnessStatus.valueOf(rs.getString("freshness_status")),
                Optional.of(rs.getInt("revision_number")),
                Optional.of(rs.getTimestamp("fetched_at").toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)),
                optionalLong(rs, "impressions"), optionalLong(rs, "reach"), optionalLong(rs, "clicks"),
                optionalLong(rs, "conversions"), optionalMoney(rs, "spend"), optionalMoney(rs, "revenue")), args);
        if (rows.isEmpty()) {
            return metricsView(type, entityUuid, window, false, FreshnessStatus.UNAVAILABLE, Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());
        }
        return rows.getFirst();
    }

    private Optional<Stage4DViews.MetricsView> matching(UUID account, PlatformEntityType type, UUID entityUuid,
            Window window, String fingerprint) {
        String sql = "SELECT revision_number, fetched_at, freshness_status, impressions, reach, clicks, conversions, spend, revenue "
                + "FROM platform_metric_snapshots WHERE platform_account_uuid=? AND entity_type=? AND "
                + entityPredicate(type) + " AND window_start=? AND window_end=? AND timezone='Asia/Taipei' "
                + "AND attribution_click_days=7 AND attribution_view_days=1 AND currency='TWD' AND source_fingerprint=?";
        Object[] args = append(params(account, type, entityUuid, window), fingerprint);
        List<Stage4DViews.MetricsView> rows = jdbc.query(sql, (rs, n) -> metricsView(type, entityUuid, window, true,
                FreshnessStatus.valueOf(rs.getString("freshness_status")),
                Optional.of(rs.getInt("revision_number")),
                Optional.of(rs.getTimestamp("fetched_at").toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)),
                optionalLong(rs, "impressions"), optionalLong(rs, "reach"), optionalLong(rs, "clicks"),
                optionalLong(rs, "conversions"), optionalMoney(rs, "spend"), optionalMoney(rs, "revenue")), args);
        return rows.stream().findFirst();
    }

    private static Stage4DViews.MetricsView metricsView(PlatformEntityType type, UUID entityUuid, Window window,
            boolean present, FreshnessStatus freshness, Optional<Integer> revision, Optional<Instant> fetchedAt,
            Optional<Long> impressions, Optional<Long> reach, Optional<Long> clicks, Optional<Long> conversions,
            Optional<BigDecimal> spend, Optional<BigDecimal> revenue) {
        return new Stage4DViews.MetricsView(type, entityUuid, window.start(), window.end(), "Asia/Taipei", 7, 1, "TWD",
                present, freshness, revision, fetchedAt, impressions, reach, clicks, conversions,
                spend.map(Stage4DTransactions::money), revenue.map(Stage4DTransactions::money),
                ratio(clicks, impressions), moneyRatio(spend, clicks),
                cpm(spend, impressions), moneyRatio(spend, conversions), ratio(conversions, clicks),
                moneyOverMoney(revenue, spend), Stage4DViews.WARNINGS);
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

    private Stage4DViews.DeliveryView view(EntityRow row) {
        return new Stage4DViews.DeliveryView(row.type(), row.uuid(), row.desired(), row.observed(),
                Optional.ofNullable(row.externalId()).filter(id -> !id.isBlank()).map(Stage4CSupport::externalFingerprint),
                row.updatedAt(), row.version());
    }

    private PlatformAuditEvent observationEvent(EntityRow before, EntityRow after) {
        PlatformAuditSubjectType subject = switch (before.type()) {
            case CAMPAIGN -> PlatformAuditSubjectType.PLATFORM_CAMPAIGN;
            case AD_SET -> PlatformAuditSubjectType.PLATFORM_AD_SET;
            case AD -> PlatformAuditSubjectType.PLATFORM_AD;
        };
        return new PlatformAuditEvent(subject, before.uuid(), AuditAction.UPDATE,
                PlatformAuditEventKind.ENTITY_RESULT_APPLIED, UUID.randomUUID(), PlatformOperationType.PAUSE,
                before.type(), before.uuid(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), before.observed(), after.observed(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private UUID account() {
        return liveInsights ? metaLiveAccount() : stage4b.account();
    }

    private UUID metaLiveAccount() {
        boolean test = Arrays.asList(environment.getActiveProfiles()).contains("test");
        UUID id = test ? Stage8CAccountInitializer.TEST_UUID : Stage8CAccountInitializer.LOCAL_UUID;
        String reference = test ? "stage8c-meta-test" : "stage8c-meta-local";
        String expectedEnvironment = test ? "TEST" : "LOCAL";
        String fingerprint = test ? Stage8CAccountInitializer.TEST_FINGERPRINT : Stage8CAccountInitializer.LOCAL_FINGERPRINT;
        List<UUID> candidates = jdbc.query(
                "SELECT platform_account_uuid FROM platform_accounts WHERE provider_key=? AND account_reference=?",
                (rs, n) -> rs.getObject(1, UUID.class), "META", reference);
        if (candidates.size() != 1 || !id.equals(candidates.getFirst())) {
            throw new Stage4BException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Integer exact = jdbc.queryForObject("""
                SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='META'
                  AND environment=? AND account_reference=? AND external_account_fingerprint=?
                  AND lifecycle_status='ACTIVE' AND archived_at IS NULL AND currency='TWD' AND timezone='Asia/Taipei'
                """, Integer.class, id, expectedEnvironment, reference, fingerprint);
        if (exact == null || exact != 1) {
            throw new Stage4BException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return id;
    }

    private static String table(PlatformEntityType type) {
        return switch (type) {
            case CAMPAIGN -> "platform_campaigns";
            case AD_SET -> "platform_ad_sets";
            case AD -> "platform_ads";
        };
    }

    private static String column(PlatformEntityType type) {
        return switch (type) {
            case CAMPAIGN -> "platform_campaign_uuid";
            case AD_SET -> "platform_ad_set_uuid";
            case AD -> "platform_ad_uuid";
        };
    }

    private static String entityPredicate(PlatformEntityType type) {
        return column(type) + "=?";
    }

    private static Object[] params(UUID account, PlatformEntityType type, UUID entityUuid, Window window) {
        return new Object[] {account, type.name(), entityUuid, Timestamp.from(window.start()), Timestamp.from(window.end())};
    }

    private static Object[] append(Object[] base, Object extra) {
        Object[] copy = new Object[base.length + 1];
        System.arraycopy(base, 0, copy, 0, base.length);
        copy[base.length] = extra;
        return copy;
    }

    private static Optional<Long> optionalLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<BigDecimal> optionalMoney(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private static String money(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static String sqlState(DataAccessException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof java.sql.SQLException sql ? sql.getSQLState() : null;
    }

    private static Stage4BException notFound() {
        return new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    private static Stage4BException contract() {
        return new Stage4BException("PLATFORM_CONTRACT_INVALID", HttpStatus.BAD_REQUEST);
    }

    private static Stage4BException conflict() {
        return new Stage4BException("PLATFORM_REFRESH_CONCURRENCY_CONFLICT", HttpStatus.CONFLICT);
    }

    record Eligibility(UUID account, EntityRow row, Window window) {}
    record EntityRow(PlatformEntityType type, UUID uuid, PlatformDesiredState desired,
            Optional<PlatformObservedState> observed, String externalId, Instant updatedAt, long version) {}
    record Window(Instant start, Instant end) {}
}
