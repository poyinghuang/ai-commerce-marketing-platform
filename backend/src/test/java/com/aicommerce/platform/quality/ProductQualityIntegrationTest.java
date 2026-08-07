package com.aicommerce.platform.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.asset.application.AssetCommandService;
import com.aicommerce.platform.asset.application.CreateAssetCommand;
import com.aicommerce.platform.asset.domain.AssetType;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.campaign.application.CampaignCommandService;
import com.aicommerce.platform.campaign.application.CreateCampaignCommand;
import com.aicommerce.platform.campaign.application.CreateCampaignProductCommand;
import com.aicommerce.platform.creativeplan.application.CreateCreativePlanCommand;
import com.aicommerce.platform.creativeplan.application.CreativePlanCommandService;
import com.aicommerce.platform.knowledge.application.CreateKnowledgeCommand;
import com.aicommerce.platform.knowledge.application.KnowledgeCommandService;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.PatchField;
import com.aicommerce.platform.product.application.PatchProductCommand;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.quality.application.ProductQualityQueryService;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductQualityIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired ProductCommandService products;
    @Autowired KnowledgeCommandService knowledge;
    @Autowired CreativePlanCommandService creativePlans;
    @Autowired CampaignCommandService campaigns;
    @Autowired AssetCommandService assets;
    @Autowired ProductQualityQueryService quality;
    @Autowired ProductQualityRecalculationService recalculation;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void productAndKnowledgeMutationsRecalculateAndAuditWithinTheirOperation() {
        Product product = create("quality-create");
        var initial = quality.get(product.getProductUuid());
        assertThat(initial.productMasterScore()).isEqualTo(35);
        assertThat(initial.blockers()).extracting(value -> value.code())
                .contains("KNOWLEDGE_MISSING", "CREATIVE_PLAN_MISSING", "IMAGE_ASSET_MISSING");
        assertThat(auditTypes("quality-create"))
                .containsExactlyInAnyOrder("PRODUCT", "QUALITY_SCORE", "WORKFLOW_STATUS");

        long version = initial.version();
        knowledge.create(product.getProductUuid(),
                new CreateKnowledgeCommand(KnowledgeType.FEATURE, "Feature", "Content", "Source"),
                "quality-knowledge");
        var recalculated = quality.get(product.getProductUuid());
        assertThat(recalculated.version()).isGreaterThan(version);
        assertThat(recalculated.productKnowledgeScore()).isEqualTo(10);
        assertThat(recalculated.blockers()).extracting(value -> value.code())
                .doesNotContain("KNOWLEDGE_MISSING");
        assertThat(auditTypes("quality-knowledge"))
                .containsExactlyInAnyOrder("PRODUCT_KNOWLEDGE", "QUALITY_SCORE", "WORKFLOW_STATUS");
        assertThat(jdbc.queryForList("select distinct operation_uuid from audit_logs where request_id=?",
                UUID.class, "quality-knowledge")).hasSize(1);
    }

    @Test
    void manualAdjustmentUsesEtagValidationTrustedActorAndIdempotency() throws Exception {
        Product product = create("quality-adjust");
        String path = "/api/products/" + product.getProductUuid() + "/quality";
        String etag = mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("ETag");

        mvc.perform(patch(path + "/manual-adjustment")
                .contentType("application/merge-patch+json").content("{\"manualAdjustment\":10}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(patch(path + "/manual-adjustment").header("If-Match", "10")
                .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_IF_MATCH"));
        mvc.perform(patch(path + "/manual-adjustment").header("If-Match", etag)
                .contentType("application/merge-patch+json").content("{\"manualAdjustment\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));

        String adjustedEtag = mvc.perform(patch(path + "/manual-adjustment").header("If-Match", etag)
                .header("X-Request-ID", "quality-adjust-patch")
                .contentType("application/merge-patch+json")
                .content("{\"manualAdjustment\":10,\"reason\":\"Reviewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualAdjustment").value(10))
                .andExpect(jsonPath("$.manualAdjustedBy").value("local-admin"))
                .andExpect(jsonPath("$.finalScore").value(45))
                .andReturn().getResponse().getHeader("ETag");
        assertThat(adjustedEtag).isNotEqualTo(etag);

        mvc.perform(patch(path + "/manual-adjustment").header("If-Match", etag)
                .contentType("application/merge-patch+json")
                .content("{\"manualAdjustment\":0}"))
                .andExpect(status().isPreconditionFailed());
        int beforeNoOp = auditCount("quality-adjust-noop");
        mvc.perform(patch(path + "/manual-adjustment").header("If-Match", adjustedEtag)
                .header("X-Request-ID", "quality-adjust-noop")
                .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", adjustedEtag));
        assertThat(auditCount("quality-adjust-noop")).isEqualTo(beforeNoOp);

        String resetEtag = mvc.perform(patch(path + "/manual-adjustment").header("If-Match", adjustedEtag)
                .contentType("application/merge-patch+json").content("{\"manualAdjustment\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.manualAdjustment").value(0))
                .andExpect(jsonPath("$.manualAdjustmentReason").doesNotExist())
                .andReturn().getResponse().getHeader("ETag");
        products.archive(product.getProductUuid(), product.getVersion(), "quality-adjust-archive");
        mvc.perform(patch(path + "/manual-adjustment").header("If-Match", resetEtag)
                .contentType("application/merge-patch+json")
                .content("{\"manualAdjustment\":1,\"reason\":\"Blocked\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_ARCHIVED"));
    }

    @Test
    void outerTransactionRollbackRemovesProductQualityAndAuditChangesTogether() {
        Product product = create("quality-rollback-create");
        long productVersion = product.getVersion();
        long qualityVersion = quality.get(product.getProductUuid()).version();
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            products.patch(product.getProductUuid(), productVersion, brandPatch("Changed"), "quality-rollback");
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select version from products where product_uuid=?", Long.class,
                product.getProductUuid())).isEqualTo(productVersion);
        assertThat(quality.get(product.getProductUuid()).version()).isEqualTo(qualityVersion);
        assertThat(auditCount("quality-rollback")).isZero();
    }

    @Test
    void everySourceMutationProgressesTheProjectionToReady() {
        Product product = create("quality-progress-product");
        for (KnowledgeType type : java.util.List.of(KnowledgeType.FEATURE, KnowledgeType.BENEFIT,
                KnowledgeType.AUDIENCE, KnowledgeType.PROOF)) {
            knowledge.create(product.getProductUuid(), new CreateKnowledgeCommand(type, type.name(), "Content", null),
                    "quality-progress-knowledge-" + type.name());
        }
        assertThat(quality.get(product.getProductUuid()).productKnowledgeScore()).isEqualTo(25);

        var plan = creativePlans.create(product.getProductUuid(), new CreateCreativePlanCommand("Plan", "Audience",
                null, "Pain", "Benefit", "Angle", null, "Tone", "Style", null, null, "Buy"),
                "quality-progress-plan");
        assertThat(quality.get(product.getProductUuid()).creativePlanScore()).isEqualTo(25);

        var campaign = campaigns.create(new CreateCampaignCommand("Campaign", null, LocalDate.now(), null,
                "Objective", null, BigDecimal.TEN, null, "TWD", null, "https://example.com/landing"),
                "quality-progress-campaign");
        campaigns.addProduct(campaign.getCampaignUuid(),
                new CreateCampaignProductCommand(product.getProductUuid(), "PRIMARY", 1, BigDecimal.valueOf(100)),
                "quality-progress-association");
        assertThat(quality.get(product.getProductUuid()).campaignReadinessScore()).isEqualTo(5);

        assets.create(product.getProductUuid(), new CreateAssetCommand(plan.getCreativePlanUuid(),
                campaign.getCampaignUuid(), AssetType.IMAGE, "Hero", null, null,
                "https://example.com/image.jpg", "image/jpeg", "image.jpg", 10L, null, Map.of()),
                "quality-progress-asset");
        var ready = quality.get(product.getProductUuid());
        assertThat(ready.assetMetadataScore()).isEqualTo(10);
        assertThat(ready.systemScore()).isEqualTo(100);
        assertThat(ready.finalScore()).isEqualTo(100);
        assertThat(ready.blockers()).isEmpty();
        assertThat(ready.readinessStatus().name()).isEqualTo("READY");
    }

    @Test
    void systemRepairUsesOneGeneratedOperationAndNonemptyRequestId() {
        Product product = create("quality-repair-create");
        jdbc.update("update quality_scores set product_master_score=0, system_score=0, final_score=0 "
                + "where product_uuid=?", product.getProductUuid());
        var context = contexts.forSystem("quality-repair");

        var repaired = recalculation.recalculate(product.getProductUuid(), context);

        assertThat(repaired.productMasterScore()).isEqualTo(35);
        assertThat(context.requestId()).isNotBlank();
        assertThat(jdbc.queryForList("select distinct operation_uuid from audit_logs where request_id=?",
                UUID.class, context.requestId())).containsExactly(context.operationUuid());
        assertThat(jdbc.queryForList("select distinct actor_type from audit_logs where request_id=?",
                String.class, context.requestId())).containsExactly("SYSTEM");
        assertThat(jdbc.queryForList("select distinct source from audit_logs where request_id=?",
                String.class, context.requestId())).containsExactly("SYSTEM");
    }

    private Product create(String requestId) {
        return products.create(new CreateProductCommand("SKU-" + UUID.randomUUID(), "Product", "Brand", "Category",
                null, "Description", BigDecimal.ONE, BigDecimal.TEN, "TWD", 5L,
                "https://example.com/product"), requestId);
    }
    private PatchProductCommand brandPatch(String brand) {
        return new PatchProductCommand(PatchField.absent(), PatchField.absent(), PatchField.present(brand),
                PatchField.absent(), PatchField.absent(), PatchField.absent(), PatchField.absent(),
                PatchField.absent(), PatchField.absent(), PatchField.absent(), PatchField.absent());
    }
    private java.util.List<String> auditTypes(String requestId) {
        return jdbc.queryForList("select entity_type from audit_logs where request_id=? order by entity_type",
                String.class, requestId);
    }
    private int auditCount(String requestId) {
        return jdbc.queryForObject("select count(*) from audit_logs where request_id=?", Integer.class, requestId);
    }
}
