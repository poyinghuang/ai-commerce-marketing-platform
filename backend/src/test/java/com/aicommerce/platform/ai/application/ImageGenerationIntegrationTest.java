package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.aicommerce.platform.ai.infrastructure.provider.LocalImagePromptBootstrap;
import com.aicommerce.platform.ai.infrastructure.provider.StubAssetBinaryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ImageGenerationIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @DynamicPropertySource
    static void budget(DynamicPropertyRegistry registry) {
        registry.add("AI_BUDGET_CURRENCY", () -> "USD");
        registry.add("AI_MAX_JOB_COST", () -> "10.000000");
        registry.add("AI_MAX_BATCH_COST", () -> "10.000000");
        registry.add("AI_MAX_DAILY_COST", () -> "1000.000000");
    }

    @Autowired ImageGenerationService service;
    @Autowired TextGenerationService queries;
    @Autowired JdbcTemplate jdbc;
    @Autowired TextGenerationExecutionTransactions execution;
    @Autowired AuditOperationContextFactory contexts;
    UUID productUuid;
    UUID planUuid;
    UUID sourceAssetUuid;

    @BeforeEach
    void setUp() {
        productUuid = UUID.randomUUID();
        planUuid = UUID.randomUUID();
        sourceAssetUuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products(product_uuid, product_id, product_name, brand, lifecycle_status, version)
                VALUES (?, ?, 'Image Product', 'Stage 03', 'ACTIVE', 0)
                """, productUuid, productId(productUuid));
        jdbc.update("""
                INSERT INTO creative_plans(creative_plan_uuid, product_uuid, plan_name, visual_style)
                VALUES (?, ?, 'Image Plan', 'Clean studio')
                """, planUuid, productUuid);
        byte[] fixture = StubAssetBinaryStore.fixture();
        jdbc.update("""
                INSERT INTO assets(asset_uuid, product_uuid, asset_type, purpose, storage_provider,
                    provider_file_id, media_type, original_filename, size_bytes, checksum_sha256)
                VALUES (?, ?, 'IMAGE', 'PRODUCT_SOURCE', 'LOCAL_STUB', ?, 'image/png',
                    'source.png', ?, ?)
                """, sourceAssetUuid, productUuid, StubAssetBinaryStore.SOURCE_HANDLE,
                fixture.length, StubAssetBinaryStore.sha256(fixture));
    }

    @Test
    void backgroundCompositePersistsProtectedEvidenceGeneratedAssetAndAudit() {
        var created = service.createBatch(new CreateImageGenerationBatchCommand(productUuid, planUuid,
                LocalImagePromptBootstrap.TEMPLATE_KEY, ImageGenerationService.WORKFLOW_KEY,
                "STANDARD_IMAGE", sourceAssetUuid, null), "image-create");
        assertThat(created.jobs()).hasSize(1);
        var job = created.jobs().getFirst();

        var output = service.execute(job.getGenerationJobUuid(), job.getVersion(), "image-execute");

        assertThat(output.getGenerationType().name()).isEqualTo("IMAGE");
        assertThat(output.getSourceAssetUuid()).isEqualTo(sourceAssetUuid);
        assertThat(output.getGeneratedAssetUuid()).isNotNull();
        assertThat(output.getPreservationStatus()).isEqualTo("PASSED");
        assertThat(output.getProtectedPixelsSha256()).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assets WHERE asset_uuid=? AND purpose='AI_BACKGROUND_COMPOSITE'",
                Integer.class, output.getGeneratedAssetUuid())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT operation_uuid) FROM audit_logs WHERE request_id='image-execute'",
                Integer.class)).isEqualTo(1);
        assertThat(queries.getJob(job.getGenerationJobUuid()).getStatus().name()).isEqualTo("SUCCEEDED");

        int auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='image-execute'",
                Integer.class);
        assertThatThrownBy(() -> service.execute(job.getGenerationJobUuid(),
                queries.getJob(job.getGenerationJobUuid()).getVersion(), "image-duplicate"))
                .isInstanceOf(AiGenerationException.class)
                .extracting(value -> ((AiGenerationException) value).code())
                .isEqualTo("AI_GENERATION_STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='image-execute'",
                Integer.class)).isEqualTo(auditCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='image-duplicate'",
                Integer.class)).isZero();
    }

    @Test
    void directSqlRejectsInvalidImageModeAndImmutableOutputMutationOrDelete() {
        var created = service.createBatch(new CreateImageGenerationBatchCommand(productUuid, planUuid,
                LocalImagePromptBootstrap.TEMPLATE_KEY, ImageGenerationService.WORKFLOW_KEY,
                "STANDARD_IMAGE", sourceAssetUuid, null), "image-schema-create");
        var output = service.execute(created.jobs().getFirst().getGenerationJobUuid(),
                created.jobs().getFirst().getVersion(), "image-schema-execute");

        assertRejected("UPDATE ai_generation_outputs SET workflow_key='other' WHERE generation_output_uuid=?",
                output.getGenerationOutputUuid());
        assertRejected("DELETE FROM ai_generation_outputs WHERE generation_output_uuid=?",
                output.getGenerationOutputUuid());

        var invalid = service.createBatch(new CreateImageGenerationBatchCommand(productUuid, planUuid,
                LocalImagePromptBootstrap.TEMPLATE_KEY, ImageGenerationService.WORKFLOW_KEY,
                "STANDARD_IMAGE", sourceAssetUuid, null), "image-invalid-create");
        UUID generatedAsset = UUID.randomUUID();
        byte[] fixture = StubAssetBinaryStore.fixture();
        jdbc.update("""
                INSERT INTO assets(asset_uuid, product_uuid, creative_plan_uuid, asset_type, purpose,
                    storage_provider, provider_file_id, media_type, original_filename, size_bytes, checksum_sha256)
                VALUES (?, ?, ?, 'IMAGE', 'AI_BACKGROUND_COMPOSITE', 'LOCAL_STUB', ?, 'image/png',
                    'generated.png', ?, ?)
                """, generatedAsset, productUuid, planUuid, "generated-invalid", fixture.length,
                StubAssetBinaryStore.sha256(fixture));
        String checksum = StubAssetBinaryStore.sha256(fixture);
        String insertImage = """
                INSERT INTO ai_generation_outputs(
                    generation_output_uuid, generation_job_uuid, generation_batch_uuid, product_uuid,
                    generation_type, model_label, input_units, output_units, actual_cost, currency,
                    safety_findings, provider_metadata, source_asset_uuid, generated_asset_uuid,
                    generation_mode, workflow_key, workflow_version, image_width, image_height,
                    media_type, size_bytes, source_checksum_sha256, output_checksum_sha256,
                    protected_pixels_sha256, preservation_algorithm, preservation_status, preservation_details)
                VALUES (?, ?, ?, ?, 'IMAGE', 'stub-image', 0, 0, 0, 'USD', '[]'::jsonb, '{}'::jsonb,
                    ?, ?, ?, 'background-composite-v1', '1', 4, 4, 'image/png', ?, ?, ?, ?,
                    'RGBA_MASK_EXACT_V1', 'PASSED', ?::jsonb)
                """;
        Object[] common = {invalid.jobs().getFirst().getGenerationJobUuid(),
                invalid.batch().getGenerationBatchUuid(), productUuid, sourceAssetUuid, generatedAsset,
                fixture.length, checksum, checksum, checksum};
        assertRejected(insertImage, UUID.randomUUID(), common[0], common[1], common[2], common[3], common[4],
                "REDRAW", common[5], common[6], common[7], common[8],
                "{\"changedPixelCount\":0,\"protectedPixelCount\":4}");
        assertRejected(insertImage, UUID.randomUUID(), common[0], common[1], common[2], common[3], common[4],
                "BACKGROUND_COMPOSITE", common[5], common[6], common[7], common[8],
                "{\"changedPixelCount\":0,\"protectedPixelCount\":4,\"providerUrl\":\"secret\"}");
        UUID otherProduct = UUID.randomUUID();
        UUID otherSource = UUID.randomUUID();
        jdbc.update("INSERT INTO products(product_uuid, product_id, product_name, lifecycle_status, version) "
                + "VALUES (?, ?, 'Other Product', 'ACTIVE', 0)", otherProduct, productId(otherProduct));
        jdbc.update("""
                INSERT INTO assets(asset_uuid, product_uuid, asset_type, purpose, storage_provider,
                    provider_file_id, media_type, original_filename, size_bytes, checksum_sha256)
                VALUES (?, ?, 'IMAGE', 'PRODUCT_SOURCE', 'LOCAL_STUB', ?, 'image/png', 'other.png', ?, ?)
                """, otherSource, otherProduct, StubAssetBinaryStore.SOURCE_HANDLE, fixture.length, checksum);
        assertRejected(insertImage, UUID.randomUUID(), common[0], common[1], common[2], otherSource, common[4],
                "BACKGROUND_COMPOSITE", common[5], common[6], common[7], common[8],
                "{\"changedPixelCount\":0,\"protectedPixelCount\":4}");
    }

    @Test
    void runningImageJobCanResumeWithoutRepeatingStateTransitionAudit() {
        var created = service.createBatch(new CreateImageGenerationBatchCommand(productUuid, planUuid,
                LocalImagePromptBootstrap.TEMPLATE_KEY, ImageGenerationService.WORKFLOW_KEY,
                "STANDARD_IMAGE", sourceAssetUuid, null), "image-resume-create");
        UUID jobUuid = created.jobs().getFirst().getGenerationJobUuid();
        execution.prepareImage(jobUuid, created.jobs().getFirst().getVersion(),
                contexts.forCurrentActor("image-resume-first"));
        long runningVersion = queries.getJob(jobUuid).getVersion();

        execution.prepareImage(jobUuid, runningVersion, contexts.forCurrentActor("image-resume-second"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='image-resume-first'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE request_id='image-resume-second'",
                Integer.class)).isZero();
        assertThat(queries.getJob(jobUuid).getStatus().name()).isEqualTo("RUNNING");
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private String productId(UUID uuid) {
        return "PROD-" + String.format("%08d", Math.abs(uuid.hashCode()) % 100000000);
    }
}
