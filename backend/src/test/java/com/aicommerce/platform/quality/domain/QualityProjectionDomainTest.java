package com.aicommerce.platform.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class QualityProjectionDomainTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void assessmentAndWorkflowNoOpsDoNotReplaceEvaluationTimestamps() {
        QualityAssessment assessment = assessment(80, 0, 80, ReadinessStatus.NEEDS_REVIEW);
        QualityScore score = QualityScore.create(UUID.randomUUID(), UUID.randomUUID(), assessment, NOW);
        WorkflowStatus workflow = WorkflowStatus.create(UUID.randomUUID(), score.getProductUuid(),
                ReadinessStatus.NEEDS_REVIEW, "Review required", NOW);

        assertThat(score.applyAssessment(assessment, NOW.plusSeconds(60))).isFalse();
        assertThat(score.getCalculatedAt()).isEqualTo(NOW);
        assertThat(workflow.apply(ReadinessStatus.NEEDS_REVIEW, "Review required", NOW.plusSeconds(60))).isFalse();
        assertThat(workflow.getEvaluatedAt()).isEqualTo(NOW);
    }

    @Test
    void manualAdjustmentRequiresMetadataAndHasIdempotentReset() {
        QualityScore score = QualityScore.create(UUID.randomUUID(), UUID.randomUUID(),
                assessment(80, 0, 80, ReadinessStatus.NEEDS_REVIEW), NOW);

        assertThatThrownBy(() -> score.recordManualAdjustment(1, " ", "actor", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(score.recordManualAdjustment(10, " Review ", " local-admin ", NOW)).isTrue();
        assertThat(score.getFinalScore()).isEqualTo(90);
        assertThat(score.getManualAdjustmentReason()).isEqualTo("Review");
        assertThat(score.recordManualAdjustment(10, "Review", "local-admin", NOW.plusSeconds(1))).isFalse();
        assertThat(score.recordManualAdjustment(0, null, null, NOW.plusSeconds(2))).isTrue();
        assertThat(score.recordManualAdjustment(0, null, null, NOW.plusSeconds(3))).isFalse();
        assertThat(score.getManualAdjustmentReason()).isNull();
    }

    @Test
    void recalculationCannotSilentlyChangeManualAdjustment() {
        QualityScore score = QualityScore.create(UUID.randomUUID(), UUID.randomUUID(),
                assessment(80, 0, 80, ReadinessStatus.NEEDS_REVIEW), NOW);

        assertThatThrownBy(() -> score.applyAssessment(
                assessment(80, 1, 81, ReadinessStatus.NEEDS_REVIEW), NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retain the persisted manual adjustment");
    }

    private QualityAssessment assessment(int system, int adjustment, int finalScore, ReadinessStatus status) {
        int master = Math.min(35, system);
        int knowledge = Math.min(25, system - master);
        int creative = system - master - knowledge;
        return new QualityAssessment(master, knowledge, creative, 0, 0,
                system, adjustment, finalScore, Set.of(), status);
    }
}
