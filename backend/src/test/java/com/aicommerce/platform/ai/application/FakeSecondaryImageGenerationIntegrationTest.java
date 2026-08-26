package com.aicommerce.platform.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.aicommerce.platform.ai.infrastructure.provider.FakeSecondaryImageGenerationProvider;
import com.aicommerce.platform.ai.infrastructure.provider.LocalImagePromptBootstrap;
import com.aicommerce.platform.ai.infrastructure.provider.StubAssetBinaryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "platform.image.provider=fake-secondary")
class FakeSecondaryImageGenerationIntegrationTest {
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
    @Autowired ImageGenerationProvider imageProvider;
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
    void generatePersistsSecondaryProviderKeyAndLeavesPreservationUnchanged() {
        assertThat(imageProvider).isExactlyInstanceOf(FakeSecondaryImageGenerationProvider.class);

        var created = service.createBatch(new CreateImageGenerationBatchCommand(productUuid, planUuid,
                LocalImagePromptBootstrap.TEMPLATE_KEY, ImageGenerationService.WORKFLOW_KEY,
                "STANDARD_IMAGE", sourceAssetUuid, null), "fake-secondary-create");
        assertThat(created.jobs()).hasSize(1);
        var job = created.jobs().getFirst();
        assertThat(jdbc.queryForObject(
                "SELECT provider_key FROM ai_generation_jobs WHERE generation_job_uuid=?",
                String.class, job.getGenerationJobUuid())).isEqualTo("FAKE_SECONDARY_IMAGE");
        assertThat(jdbc.queryForObject(
                "SELECT model_key FROM ai_generation_jobs WHERE generation_job_uuid=?",
                String.class, job.getGenerationJobUuid())).isEqualTo("deterministic-fake-secondary");

        var output = service.execute(job.getGenerationJobUuid(), job.getVersion(), "fake-secondary-execute");

        assertThat(output.getPreservationStatus()).isEqualTo("PASSED");
        assertThat(output.getPreservationAlgorithm()).isEqualTo("RGBA_MASK_EXACT_V1");
        assertThat(output.getWorkflowKey()).isEqualTo(ImageGenerationService.WORKFLOW_KEY);
        assertThat(output.getWorkflowVersion()).isEqualTo(ImageGenerationService.WORKFLOW_VERSION);
        assertThat(output.getModelLabel()).isEqualTo("deterministic-fake-secondary");
        assertThat(output.getActualCost()).isEqualByComparingTo("0");
        assertThat(queries.getJob(job.getGenerationJobUuid()).getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT operation_uuid) FROM audit_logs WHERE request_id='fake-secondary-execute'",
                Integer.class)).isEqualTo(1);
    }

    private String productId(UUID uuid) {
        return "PROD-" + String.format("%08d", Math.abs(uuid.hashCode()) % 100000000);
    }
}
