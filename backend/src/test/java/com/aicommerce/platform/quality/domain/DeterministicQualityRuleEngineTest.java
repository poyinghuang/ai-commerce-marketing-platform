package com.aicommerce.platform.quality.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.AssetFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CampaignFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.CreativePlanFacts;
import com.aicommerce.platform.quality.domain.QualityAssessmentInput.ProductFacts;
import org.junit.jupiter.api.Test;

class DeterministicQualityRuleEngineTest {

    private final DeterministicQualityRuleEngine engine = new DeterministicQualityRuleEngine();

    @Test
    void completeActiveProductEarnsEveryPointAndIsReady() {
        QualityAssessment result = engine.assess(completeInput(0, false));

        assertThat(result.productMasterScore()).isEqualTo(35);
        assertThat(result.productKnowledgeScore()).isEqualTo(25);
        assertThat(result.creativePlanScore()).isEqualTo(25);
        assertThat(result.assetMetadataScore()).isEqualTo(10);
        assertThat(result.campaignReadinessScore()).isEqualTo(5);
        assertThat(result.systemScore()).isEqualTo(100);
        assertThat(result.finalScore()).isEqualTo(100);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.readinessStatus()).isEqualTo(ReadinessStatus.READY);
    }

    @Test
    void emptyInputProducesAllContentBlockersAndDraft() {
        var input = new QualityAssessmentInput(
                new ProductFacts(false, null, null, null, null, null, null, null, null, null),
                Set.of(), List.of(), List.of(), List.of(), 0);

        QualityAssessment result = engine.assess(input);

        assertThat(result.systemScore()).isZero();
        assertThat(result.blockers()).containsExactlyInAnyOrder(
                QualityBlockerCode.PRODUCT_NAME_MISSING,
                QualityBlockerCode.SALE_PRICE_MISSING,
                QualityBlockerCode.CURRENCY_MISSING,
                QualityBlockerCode.KNOWLEDGE_MISSING,
                QualityBlockerCode.CREATIVE_PLAN_MISSING,
                QualityBlockerCode.IMAGE_ASSET_MISSING);
        assertThat(result.readinessStatus()).isEqualTo(ReadinessStatus.DRAFT);
    }

    @Test
    void creativePlanUsesMostCompleteSinglePlanWithoutCombiningFields() {
        var plans = List.of(
                new CreativePlanFacts("Audience", "Pain", null, null, null, null, null),
                new CreativePlanFacts(null, null, "Benefit", "Angle", "Tone", "Style", "CTA"));
        var input = new QualityAssessmentInput(completeProduct(false), completeKnowledge(), plans,
                completeAssets(), completeCampaigns(), 0);

        assertThat(engine.assess(input).creativePlanScore()).isEqualTo(15);
    }

    @Test
    void adjustmentClampsButCannotRemoveBlockingReason() {
        var input = new QualityAssessmentInput(
                completeProduct(false), Set.of(), completePlans(), completeAssets(), completeCampaigns(), 20);

        QualityAssessment result = engine.assess(input);

        assertThat(result.finalScore()).isEqualTo(95);
        assertThat(result.blockers()).containsExactly(QualityBlockerCode.KNOWLEDGE_MISSING);
        assertThat(result.readinessStatus()).isEqualTo(ReadinessStatus.NEEDS_REVIEW);
    }

    @Test
    void archivedProductIsAlwaysDraftEvenAtMaximumScore() {
        QualityAssessment result = engine.assess(completeInput(0, true));

        assertThat(result.systemScore()).isEqualTo(100);
        assertThat(result.blockers()).containsExactly(QualityBlockerCode.PRODUCT_ARCHIVED);
        assertThat(result.readinessStatus()).isEqualTo(ReadinessStatus.DRAFT);
    }

    @Test
    void manualAdjustmentRangeIsValidatedAtInputBoundary() {
        assertThatThrownBy(() -> completeInput(21, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between -20 and 20");
        assertThatThrownBy(() -> completeInput(-21, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readinessThresholdsAndBlockerOverrideAreExact() {
        assertThat(engine.readinessStatus(69, false, Set.of())).isEqualTo(ReadinessStatus.DRAFT);
        assertThat(engine.readinessStatus(70, false, Set.of())).isEqualTo(ReadinessStatus.NEEDS_REVIEW);
        assertThat(engine.readinessStatus(89, false, Set.of())).isEqualTo(ReadinessStatus.NEEDS_REVIEW);
        assertThat(engine.readinessStatus(90, false, Set.of())).isEqualTo(ReadinessStatus.READY);
        assertThat(engine.readinessStatus(100, false, Set.of(QualityBlockerCode.KNOWLEDGE_MISSING)))
                .isEqualTo(ReadinessStatus.NEEDS_REVIEW);
        assertThat(engine.readinessStatus(100, true, Set.of())).isEqualTo(ReadinessStatus.DRAFT);
    }

    private QualityAssessmentInput completeInput(int adjustment, boolean archived) {
        return new QualityAssessmentInput(completeProduct(archived), completeKnowledge(), completePlans(),
                completeAssets(), completeCampaigns(), adjustment);
    }

    private ProductFacts completeProduct(boolean archived) {
        return new ProductFacts(archived, "Product", "Brand", "Category", "Description",
                BigDecimal.ONE, BigDecimal.TEN, "TWD", 3L, "https://example.com/product");
    }

    private Set<KnowledgeType> completeKnowledge() {
        return EnumSet.of(KnowledgeType.FEATURE, KnowledgeType.BENEFIT, KnowledgeType.AUDIENCE,
                KnowledgeType.PAIN_POINT);
    }

    private List<CreativePlanFacts> completePlans() {
        return List.of(new CreativePlanFacts("Audience", "Pain", "Benefit", "Angle", "Tone", "Style", "CTA"));
    }

    private List<AssetFacts> completeAssets() {
        return List.of(new AssetFacts(true, "https://example.com/image.jpg", null, null,
                "image/jpeg", "image.jpg"));
    }

    private List<CampaignFacts> completeCampaigns() {
        return List.of(new CampaignFacts("Objective", "https://example.com/landing",
                BigDecimal.ONE, null, "TWD"));
    }
}
