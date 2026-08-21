package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.aicommerce.platform.quality.domain.QualityAssessment;
import com.aicommerce.platform.quality.domain.QualityScore;
import com.aicommerce.platform.quality.domain.ReadinessStatus;
import com.aicommerce.platform.quality.domain.WorkflowStatus;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreBlockerJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.WorkflowStatusJpaRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone2DQualitySchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired QualityScoreJpaRepository qualityScores;
    @Autowired QualityScoreBlockerJpaRepository blockers;
    @Autowired WorkflowStatusJpaRepository workflows;

    @Test
    void v5CreatesOnlyApprovedTablesAndJpaMappingsValidate() {
        assertThat(List.of(flyway.info().applied()).stream()
                .filter(info -> info.getVersion() != null)
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(List.of("quality_scores", "quality_score_blockers", "workflow_status"))
                .allMatch(this::tableExists);
        assertThat(List.of(qualityScores, blockers, workflows)).doesNotContainNull();
    }

    @Test
    void scoreRangesSumsClampAiAndManualMetadataAreDatabaseEnforced() {
        UUID product = insertProduct("CONSTRAINT");
        UUID score = insertScore(product);

        assertRejected("UPDATE quality_scores SET product_master_score = 36, system_score = 36, final_score = 36 "
                + "WHERE quality_score_uuid = ?", score);
        assertRejected("UPDATE quality_scores SET system_score = 1, final_score = 1 WHERE quality_score_uuid = ?", score);
        assertRejected("UPDATE quality_scores SET ai_suggested_score = 101 WHERE quality_score_uuid = ?", score);
        assertRejected("UPDATE quality_scores SET manual_adjustment = 21, final_score = 21 WHERE quality_score_uuid = ?", score);
        assertRejected("UPDATE quality_scores SET manual_adjustment = 1, final_score = 1 WHERE quality_score_uuid = ?", score);
        assertRejected("UPDATE quality_scores SET manual_adjustment = 1, final_score = 1, "
                + "manual_adjustment_reason = ' ', manual_adjusted_by = 'actor', manual_adjusted_at = CURRENT_TIMESTAMP "
                + "WHERE quality_score_uuid = ?", score);

        jdbc.update("UPDATE quality_scores SET product_master_score = 35, product_knowledge_score = 25, "
                + "creative_plan_score = 25, asset_metadata_score = 10, campaign_readiness_score = 5, "
                + "system_score = 100, manual_adjustment = -20, manual_adjustment_reason = 'review', "
                + "manual_adjusted_by = 'local-admin', manual_adjusted_at = CURRENT_TIMESTAMP, final_score = 80 "
                + "WHERE quality_score_uuid = ?", score);
        assertThat(jdbc.queryForObject("SELECT final_score FROM quality_scores WHERE quality_score_uuid = ?",
                Integer.class, score)).isEqualTo(80);
    }

    @Test
    void blockerAndWorkflowEnumsUniquenessForeignKeysAndTextAreEnforced() {
        UUID product = insertProduct("ENUM");
        UUID score = insertScore(product);
        insertBlocker(score, "KNOWLEDGE_MISSING");

        assertRejected("INSERT INTO quality_score_blockers "
                + "(quality_score_blocker_uuid, quality_score_uuid, blocker_code, message) "
                + "VALUES (?, ?, 'UNKNOWN', 'message')", UUID.randomUUID(), score);
        assertRejected("INSERT INTO quality_score_blockers "
                + "(quality_score_blocker_uuid, quality_score_uuid, blocker_code, message) "
                + "VALUES (?, ?, 'KNOWLEDGE_MISSING', 'duplicate')", UUID.randomUUID(), score);
        assertRejected("INSERT INTO quality_score_blockers "
                + "(quality_score_blocker_uuid, quality_score_uuid, blocker_code, message) "
                + "VALUES (?, ?, 'CREATIVE_PLAN_MISSING', ' ')", UUID.randomUUID(), score);
        assertRejected("INSERT INTO quality_score_blockers "
                + "(quality_score_blocker_uuid, quality_score_uuid, blocker_code, message) "
                + "VALUES (?, ?, 'CREATIVE_PLAN_MISSING', 'message')", UUID.randomUUID(), UUID.randomUUID());

        UUID workflow = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_status "
                + "(workflow_status_uuid, product_uuid, status_reason) VALUES (?, ?, 'Initial')", workflow, product);
        assertRejected("UPDATE workflow_status SET stage = 'OTHER' WHERE workflow_status_uuid = ?", workflow);
        assertRejected("UPDATE workflow_status SET status = 'INVALID' WHERE workflow_status_uuid = ?", workflow);
        assertRejected("UPDATE workflow_status SET status_reason = ' ' WHERE workflow_status_uuid = ?", workflow);
        assertRejected("DELETE FROM quality_scores WHERE quality_score_uuid = ?", score);
    }

    @Test
    void directSqlCannotReassignProjectionIdentityOrOwner() {
        UUID product = insertProduct("IMMUTABLE-A");
        UUID other = insertProduct("IMMUTABLE-B");
        UUID score = insertScore(product);
        UUID blocker = insertBlocker(score, "KNOWLEDGE_MISSING");
        UUID workflow = UUID.randomUUID();
        jdbc.update("INSERT INTO workflow_status "
                + "(workflow_status_uuid, product_uuid, status_reason) VALUES (?, ?, 'Initial')", workflow, product);

        assertImmutable("UPDATE quality_scores SET quality_score_uuid = ? WHERE quality_score_uuid = ?",
                UUID.randomUUID(), score);
        assertImmutable("UPDATE quality_scores SET product_uuid = ? WHERE quality_score_uuid = ?", other, score);
        assertImmutable("UPDATE quality_score_blockers SET quality_score_uuid = ? WHERE quality_score_blocker_uuid = ?",
                UUID.randomUUID(), blocker);
        assertImmutable("UPDATE workflow_status SET product_uuid = ? WHERE workflow_status_uuid = ?", other, workflow);
    }

    @Test
    void jpaPersistsStringEnumsTimestampsAndOptimisticVersions() {
        UUID product = insertProduct("JPA");
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        var assessment = new QualityAssessment(35, 25, 25, 10, 5, 100, 0, 100,
                Set.of(), ReadinessStatus.READY);
        QualityScore score = qualityScores.saveAndFlush(QualityScore.create(
                UUID.randomUUID(), product, assessment, now));
        WorkflowStatus workflow = workflows.saveAndFlush(WorkflowStatus.create(
                UUID.randomUUID(), product, ReadinessStatus.READY, "All requirements met", now));

        assertThat(score.getVersion()).isZero();
        assertThat(score.getAiSuggestedScore()).isNull();
        assertThat(workflow.getVersion()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM workflow_status WHERE product_uuid = ?",
                String.class, product)).isEqualTo("READY");

        score.recordManualAdjustment(-20, "Manual review", "local-admin", now.plusSeconds(1));
        score = qualityScores.saveAndFlush(score);
        assertThat(score.getVersion()).isEqualTo(1L);
        assertThat(score.getFinalScore()).isEqualTo(80);
    }

    private UUID insertProduct(String suffix) {
        UUID uuid = UUID.randomUUID();
        Long sequence = jdbc.queryForObject("SELECT nextval('product_id_seq')", Long.class);
        jdbc.update("INSERT INTO products "
                + "(product_uuid, product_id, sku, product_name, lifecycle_status, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                uuid, "PROD-%08d".formatted(sequence), "SKU-" + suffix, "Product " + suffix);
        return uuid;
    }

    private UUID insertScore(UUID product) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO quality_scores (quality_score_uuid, product_uuid) VALUES (?, ?)", uuid, product);
        return uuid;
    }

    private UUID insertBlocker(UUID score, String code) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO quality_score_blockers "
                + "(quality_score_blocker_uuid, quality_score_uuid, blocker_code, message) VALUES (?, ?, ?, 'message')",
                uuid, score, code);
        return uuid;
    }

    private boolean tableExists(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?", Integer.class, name) == 1;
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private void assertImmutable(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("projection identity is immutable");
    }
}
