package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.audit.domain.AuditValueType;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignPlanJpaRepository;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignProductJpaRepository;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
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
class Milestone2CSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired ProductKnowledgeJpaRepository knowledgeRepository;
    @Autowired CreativePlanJpaRepository creativePlanRepository;
    @Autowired CampaignPlanJpaRepository campaignPlanRepository;
    @Autowired CampaignProductJpaRepository campaignProductRepository;
    @Autowired AssetJpaRepository assetRepository;

    @Test
    void v4TablesRemainAvailableAndRepositoriesLoadUnderLatestHibernateValidation() {
        assertThat(List.of(flyway.info().applied()).stream()
                .map(info -> info.getVersion().getVersion()))
                .containsExactly("1", "2", "3", "4", "5", "6", "6.1", "7", "8", "9", "10", "11", "12", "13", "14");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(List.of("product_knowledge", "creative_plans", "campaign_plans", "campaign_products", "assets"))
                .allMatch(this::tableExists);
        assertThat(List.of(knowledgeRepository, creativePlanRepository, campaignPlanRepository,
                campaignProductRepository, assetRepository)).allMatch(repository -> repository != null);
    }

    @Test
    void stringEnumsPersistEveryApprovedValueAndRejectUnknownValues() {
        UUID productUuid = insertProduct("ENUM");
        for (KnowledgeType type : KnowledgeType.values()) {
            UUID knowledgeUuid = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO product_knowledge
                        (knowledge_uuid, product_uuid, knowledge_type, title, content)
                    VALUES (?, ?, ?, 'Title', 'Content')
                    """, knowledgeUuid, productUuid, type.name());
            assertThat(jdbc.queryForObject(
                    "SELECT knowledge_type FROM product_knowledge WHERE knowledge_uuid = ?",
                    String.class, knowledgeUuid)).isEqualTo(type.name());
        }
        for (String type : List.of("IMAGE", "VIDEO", "DOCUMENT", "OTHER")) {
            UUID assetUuid = UUID.randomUUID();
            jdbc.update("INSERT INTO assets (asset_uuid, product_uuid, asset_type) VALUES (?, ?, ?)",
                    assetUuid, productUuid, type);
            assertThat(jdbc.queryForObject("SELECT asset_type FROM assets WHERE asset_uuid = ?", String.class,
                    assetUuid)).isEqualTo(type);
        }

        UUID auditUuid = insertAudit(productUuid);
        int order = 0;
        for (AuditValueType type : AuditValueType.values()) {
            jdbc.update("""
                    INSERT INTO audit_log_changes
                        (audit_change_uuid, audit_uuid, field_name, value_type, change_order)
                    VALUES (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), auditUuid, "field_" + order, type.name(), order++);
        }
        assertThat(jdbc.queryForList(
                "SELECT value_type FROM audit_log_changes WHERE audit_uuid = ? ORDER BY change_order",
                String.class, auditUuid)).containsExactly(
                        "STRING", "UUID", "ENUM", "TIMESTAMP", "DECIMAL", "INTEGER", "DATE");

        assertRejected("INSERT INTO product_knowledge (knowledge_uuid, product_uuid, knowledge_type, title, content) "
                + "VALUES (?, ?, 'UNKNOWN', 'Title', 'Content')", UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type) VALUES (?, ?, 'AUDIO')",
                UUID.randomUUID(), productUuid);
        assertRejected("""
                INSERT INTO audit_log_changes
                    (audit_change_uuid, audit_uuid, field_name, value_type, change_order)
                VALUES (?, ?, 'invalid', 'UNKNOWN', 100)
                """, UUID.randomUUID(), auditUuid);
    }

    @Test
    void requiredTextLifecycleDateBudgetAndUrlConstraintsAreEnforced() {
        UUID productUuid = insertProduct("CHECKS");
        assertRejected("INSERT INTO product_knowledge (knowledge_uuid, product_uuid, knowledge_type, title, content) "
                + "VALUES (?, ?, 'FEATURE', '  ', 'Content')", UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO product_knowledge (knowledge_uuid, product_uuid, knowledge_type, title, content) "
                + "VALUES (?, ?, 'FEATURE', 'Title', '  ')", UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO creative_plans (creative_plan_uuid, product_uuid, plan_name) VALUES (?, ?, '')",
                UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name) VALUES (?, '  ')",
                UUID.randomUUID());
        assertRejected("INSERT INTO product_knowledge "
                        + "(knowledge_uuid, product_uuid, knowledge_type, title, content, lifecycle_status, archived_at) "
                        + "VALUES (?, ?, 'FEATURE', 'Title', 'Content', 'ACTIVE', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO campaign_plans "
                        + "(campaign_uuid, campaign_name, start_date, end_date) "
                        + "VALUES (?, 'Bad dates', DATE '2026-08-08', DATE '2026-08-07')",
                UUID.randomUUID());
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name, budget_daily, currency) "
                + "VALUES (?, 'Negative', -1, 'TWD')", UUID.randomUUID());
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name, budget_total, currency) "
                + "VALUES (?, 'Negative total', -1, 'TWD')", UUID.randomUUID());
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name, budget_total) "
                + "VALUES (?, 'No currency', 1)", UUID.randomUUID());
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name, currency) "
                + "VALUES (?, 'Bad currency', 'twd')", UUID.randomUUID());
        assertRejected("INSERT INTO campaign_plans (campaign_uuid, campaign_name, landing_page) "
                + "VALUES (?, 'Bad URL', 'file:///tmp/a')", UUID.randomUUID());

        UUID campaignUuid = insertCampaign("CHECKS");
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid, priority) VALUES (?, ?, ?, -1)",
                UUID.randomUUID(), campaignUuid, productUuid);
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid, budget_weight) VALUES (?, ?, ?, -0.01)",
                UUID.randomUUID(), campaignUuid, productUuid);
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid, budget_weight) VALUES (?, ?, ?, 100.01)",
                UUID.randomUUID(), campaignUuid, productUuid);
        UUID lowerBoundaryProduct = insertProduct("BOUNDARY-0");
        UUID upperBoundaryProduct = insertProduct("BOUNDARY-100");
        jdbc.update("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid, budget_weight) VALUES (?, ?, ?, 0.00)",
                UUID.randomUUID(), campaignUuid, lowerBoundaryProduct);
        jdbc.update("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid, budget_weight) VALUES (?, ?, ?, 100.00)",
                UUID.randomUUID(), campaignUuid, upperBoundaryProduct);

        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type, size_bytes) "
                + "VALUES (?, ?, 'IMAGE', -1)", UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type, checksum_sha256) "
                + "VALUES (?, ?, 'IMAGE', ?)", UUID.randomUUID(), productUuid, "A".repeat(64));
        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type, file_url) "
                + "VALUES (?, ?, 'IMAGE', 'ftp://example.com/file')", UUID.randomUUID(), productUuid);
        jdbc.update("INSERT INTO assets (asset_uuid, product_uuid, asset_type, checksum_sha256, file_url) "
                        + "VALUES (?, ?, 'IMAGE', ?, 'https://example.com/file')",
                UUID.randomUUID(), productUuid, "a".repeat(64));
    }

    @Test
    void everyV4TableEnforcesLifecycleEnumAndArchiveConsistency() {
        UUID product = insertProduct("LIFECYCLE");
        UUID knowledge = insertKnowledge(product);
        UUID creative = insertCreativePlan(product);
        UUID campaign = insertCampaign("LIFECYCLE");
        UUID association = insertCampaignProduct(campaign, product);
        UUID asset = insertAsset(product, creative, campaign);

        assertLifecycleConstraints("product_knowledge", "knowledge_uuid", knowledge);
        assertLifecycleConstraints("creative_plans", "creative_plan_uuid", creative);
        assertLifecycleConstraints("campaign_plans", "campaign_uuid", campaign);
        assertLifecycleConstraints("campaign_products", "campaign_product_uuid", association);
        assertLifecycleConstraints("assets", "asset_uuid", asset);
    }

    @Test
    void jsonMetadataMustBeAnObjectWithinSixteenKiB() {
        UUID productUuid = insertProduct("JSON");
        jdbc.update("""
                INSERT INTO assets (asset_uuid, product_uuid, asset_type, provider_metadata)
                VALUES (?, ?, 'OTHER', jsonb_build_object('value', repeat('a', 16371)))
                """, UUID.randomUUID(), productUuid);
        assertRejected("""
                INSERT INTO assets (asset_uuid, product_uuid, asset_type, provider_metadata)
                VALUES (?, ?, 'OTHER', jsonb_build_object('value', repeat('a', 16372)))
                """, UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type, provider_metadata) "
                + "VALUES (?, ?, 'OTHER', '[1,2]'::jsonb)", UUID.randomUUID(), productUuid);
        assertRejected("INSERT INTO assets (asset_uuid, product_uuid, asset_type, provider_metadata) "
                + "VALUES (?, ?, 'OTHER', '\"text\"'::jsonb)", UUID.randomUUID(), productUuid);
    }

    @Test
    void foreignKeysCompositeOwnershipUniquePairAndDeleteRestrictionsAreEnforced() {
        UUID firstProduct = insertProduct("OWNER-A");
        UUID secondProduct = insertProduct("OWNER-B");
        UUID creativePlan = insertCreativePlan(firstProduct);
        UUID campaign = insertCampaign("OWNER");
        UUID association = insertCampaignProduct(campaign, firstProduct);
        insertAsset(firstProduct, creativePlan, campaign);

        assertRejected("INSERT INTO product_knowledge (knowledge_uuid, product_uuid, knowledge_type, title, content) "
                        + "VALUES (?, ?, 'FEATURE', 'Title', 'Content')",
                UUID.randomUUID(), UUID.randomUUID());
        assertRejected("INSERT INTO creative_plans (creative_plan_uuid, product_uuid, plan_name) "
                        + "VALUES (?, ?, 'Orphan')",
                UUID.randomUUID(), UUID.randomUUID());
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), firstProduct);
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid) VALUES (?, ?, ?)",
                UUID.randomUUID(), campaign, UUID.randomUUID());
        assertRejected("INSERT INTO assets "
                        + "(asset_uuid, product_uuid, creative_plan_uuid, asset_type) VALUES (?, ?, ?, 'IMAGE')",
                UUID.randomUUID(), secondProduct, creativePlan);
        assertRejected("INSERT INTO assets "
                        + "(asset_uuid, product_uuid, campaign_uuid, asset_type) VALUES (?, ?, ?, 'IMAGE')",
                UUID.randomUUID(), secondProduct, campaign);
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid) VALUES (?, ?, ?)",
                UUID.randomUUID(), campaign, firstProduct);

        jdbc.update("UPDATE campaign_products SET lifecycle_status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP "
                + "WHERE campaign_product_uuid = ?", association);
        assertRejected("INSERT INTO campaign_products "
                        + "(campaign_product_uuid, campaign_uuid, product_uuid) VALUES (?, ?, ?)",
                UUID.randomUUID(), campaign, firstProduct);
        assertRejected("DELETE FROM products WHERE product_uuid = ?", firstProduct);
        assertRejected("DELETE FROM creative_plans WHERE creative_plan_uuid = ?", creativePlan);
        assertRejected("DELETE FROM campaign_plans WHERE campaign_uuid = ?", campaign);
    }

    @Test
    void directSqlCannotReassignAnyResourceIdentityOrOwner() {
        UUID product = insertProduct("IMMUTABLE-A");
        UUID otherProduct = insertProduct("IMMUTABLE-B");
        UUID knowledge = insertKnowledge(product);
        UUID creative = insertCreativePlan(product);
        UUID campaign = insertCampaign("IMMUTABLE");
        UUID association = insertCampaignProduct(campaign, product);
        UUID asset = insertAsset(product, creative, campaign);

        assertImmutable("UPDATE product_knowledge SET knowledge_uuid = ? WHERE knowledge_uuid = ?",
                UUID.randomUUID(), knowledge);
        assertImmutable("UPDATE product_knowledge SET product_uuid = ? WHERE knowledge_uuid = ?",
                otherProduct, knowledge);
        assertImmutable("UPDATE creative_plans SET creative_plan_uuid = ? WHERE creative_plan_uuid = ?",
                UUID.randomUUID(), creative);
        assertImmutable("UPDATE creative_plans SET product_uuid = ? WHERE creative_plan_uuid = ?",
                otherProduct, creative);
        assertImmutable("UPDATE campaign_plans SET campaign_uuid = ? WHERE campaign_uuid = ?",
                UUID.randomUUID(), campaign);
        assertImmutable("UPDATE campaign_products SET campaign_product_uuid = ? WHERE campaign_product_uuid = ?",
                UUID.randomUUID(), association);
        assertImmutable("UPDATE campaign_products SET campaign_uuid = ? WHERE campaign_product_uuid = ?",
                UUID.randomUUID(), association);
        assertImmutable("UPDATE campaign_products SET product_uuid = ? WHERE campaign_product_uuid = ?",
                otherProduct, association);
        assertImmutable("UPDATE assets SET asset_uuid = ? WHERE asset_uuid = ?", UUID.randomUUID(), asset);
        assertImmutable("UPDATE assets SET product_uuid = ? WHERE asset_uuid = ?", otherProduct, asset);
        assertImmutable("UPDATE assets SET creative_plan_uuid = NULL WHERE asset_uuid = ?", asset);
        assertImmutable("UPDATE assets SET campaign_uuid = NULL WHERE asset_uuid = ?", asset);
    }

    @Test
    void jpaLifecycleUsesStringEnumOptimisticVersionAndNoOpSemantics() {
        UUID productUuid = insertProduct("JPA");
        ProductKnowledge knowledge = knowledgeRepository.saveAndFlush(ProductKnowledge.create(
                UUID.randomUUID(), productUuid, KnowledgeType.FEATURE, "Feature", "Content", null));
        assertThat(knowledge.getVersion()).isZero();
        assertThat(knowledge.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);

        assertThat(knowledge.archive(Instant.parse("2026-08-07T00:00:00Z"))).isTrue();
        knowledge = knowledgeRepository.saveAndFlush(knowledge);
        long archivedVersion = knowledge.getVersion();
        assertThat(archivedVersion).isEqualTo(1L);
        assertThat(knowledge.archive(Instant.parse("2026-08-08T00:00:00Z"))).isFalse();
        assertThat(knowledge.getVersion()).isEqualTo(archivedVersion);

        assertThat(knowledge.restore()).isTrue();
        knowledge = knowledgeRepository.saveAndFlush(knowledge);
        assertThat(knowledge.getVersion()).isEqualTo(2L);
        assertThat(knowledge.restore()).isFalse();
        assertThat(knowledge.getVersion()).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT lifecycle_status FROM product_knowledge WHERE knowledge_uuid = ?",
                String.class, knowledge.getKnowledgeUuid())).isEqualTo("ACTIVE");
    }

    private UUID insertProduct(String suffix) {
        UUID uuid = UUID.randomUUID();
        Long sequence = jdbc.queryForObject("SELECT nextval('product_id_seq')", Long.class);
        String productId = "PROD-%08d".formatted(sequence);
        jdbc.update("""
                INSERT INTO products
                    (product_uuid, product_id, sku, product_name, lifecycle_status, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, uuid, productId, "SKU-" + suffix, "Product " + suffix);
        return uuid;
    }

    private UUID insertKnowledge(UUID productUuid) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO product_knowledge "
                + "(knowledge_uuid, product_uuid, knowledge_type, title, content) "
                + "VALUES (?, ?, 'FEATURE', 'Title', 'Content')", uuid, productUuid);
        return uuid;
    }

    private UUID insertCreativePlan(UUID productUuid) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO creative_plans (creative_plan_uuid, product_uuid, plan_name) VALUES (?, ?, 'Plan')",
                uuid, productUuid);
        return uuid;
    }

    private UUID insertCampaign(String suffix) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO campaign_plans (campaign_uuid, campaign_name) VALUES (?, ?)", uuid,
                "Campaign " + suffix);
        return uuid;
    }

    private UUID insertCampaignProduct(UUID campaignUuid, UUID productUuid) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO campaign_products "
                + "(campaign_product_uuid, campaign_uuid, product_uuid) VALUES (?, ?, ?)",
                uuid, campaignUuid, productUuid);
        return uuid;
    }

    private UUID insertAsset(UUID productUuid, UUID creativePlanUuid, UUID campaignUuid) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("INSERT INTO assets "
                        + "(asset_uuid, product_uuid, creative_plan_uuid, campaign_uuid, asset_type) "
                        + "VALUES (?, ?, ?, ?, 'IMAGE')",
                uuid, productUuid, creativePlanUuid, campaignUuid);
        return uuid;
    }

    private UUID insertAudit(UUID productUuid) {
        UUID uuid = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO audit_logs
                    (audit_uuid, operation_uuid, request_id, actor_type, actor_id, source,
                     action, entity_type, entity_uuid, product_uuid, occurred_at)
                VALUES (?, ?, 'request-2c-schema', 'SYSTEM', 'schema-test', 'SYSTEM',
                        'UPDATE', 'PRODUCT', ?, ?, CURRENT_TIMESTAMP)
                """, uuid, UUID.randomUUID(), productUuid, productUuid);
        return uuid;
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args)).isInstanceOf(DataAccessException.class);
    }

    private void assertImmutable(String sql, Object... args) {
        assertThatThrownBy(() -> jdbc.update(sql, args))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("identity is immutable");
    }

    private void assertLifecycleConstraints(String table, String idColumn, UUID id) {
        assertRejected("UPDATE " + table + " SET lifecycle_status = 'INVALID' WHERE " + idColumn + " = ?", id);
        assertRejected("UPDATE " + table + " SET archived_at = CURRENT_TIMESTAMP WHERE " + idColumn + " = ?", id);
        assertRejected("UPDATE " + table + " SET lifecycle_status = 'ARCHIVED' WHERE " + idColumn + " = ?", id);
    }

    private boolean tableExists(String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName) == 1;
    }
}
