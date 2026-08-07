package com.aicommerce.platform.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import java.lang.reflect.Method;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import jakarta.persistence.EntityManagerFactory;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
class ProductAggregateIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired ProductAggregateQueryService service;
    @Autowired ProductCommandService productCommands;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void returnsEveryShapeWithLifecycleRulesAssociationAndDeterministicOrdering() {
        Product product = createProduct("Aggregate lifecycle");
        UUID activeKnowledgeLater = knowledge(product, "Active later", false, Instant.parse("2026-08-07T12:00:00Z"));
        UUID activeKnowledgeEarlier = knowledge(product, "Active earlier", false, Instant.parse("2026-08-07T11:00:00Z"));
        UUID archivedKnowledge = knowledge(product, "Archived", true, Instant.parse("2026-08-07T13:00:00Z"));
        UUID activePlan = creativePlan(product, "Active plan", false, Instant.parse("2026-08-07T12:00:00Z"));
        UUID archivedPlan = creativePlan(product, "Archived plan", true, Instant.parse("2026-08-07T13:00:00Z"));
        CampaignIds activeCampaign = campaign(product, "Active campaign", false, false,
                Instant.parse("2026-08-07T12:00:00Z"));
        campaign(product, "Archived campaign", true, false, Instant.parse("2026-08-07T14:00:00Z"));
        campaign(product, "Archived association", false, true, Instant.parse("2026-08-07T13:00:00Z"));
        UUID activeAsset = asset(product, activePlan, activeCampaign.campaignUuid(), "active.jpg", false,
                Instant.parse("2026-08-07T12:00:00Z"));
        UUID archivedAsset = asset(product, activePlan, activeCampaign.campaignUuid(), "archived.jpg", true,
                Instant.parse("2026-08-07T13:00:00Z"));

        ProductAggregateView active = service.get(product.getProductUuid(), false);
        assertThat(active.product().productUuid()).isEqualTo(product.getProductUuid());
        assertThat(active.knowledge()).extracting(ProductAggregateView.KnowledgeView::knowledgeUuid)
                .containsExactly(activeKnowledgeLater, activeKnowledgeEarlier);
        assertThat(active.creativePlans()).extracting(ProductAggregateView.CreativePlanView::creativePlanUuid)
                .containsExactly(activePlan);
        assertThat(active.campaigns()).hasSize(1);
        assertThat(active.campaigns().getFirst().campaignUuid()).isEqualTo(activeCampaign.campaignUuid());
        assertThat(active.campaigns().getFirst().association().campaignProductUuid())
                .isEqualTo(activeCampaign.associationUuid());
        assertThat(active.assets()).extracting(ProductAggregateView.AssetView::assetUuid).containsExactly(activeAsset);
        assertThat(active.assets().getFirst().providerMetadata()).containsEntry("region", "eu");

        ProductAggregateView all = service.get(product.getProductUuid(), true);
        assertThat(all.knowledge()).extracting(ProductAggregateView.KnowledgeView::knowledgeUuid)
                .containsExactly(archivedKnowledge, activeKnowledgeLater, activeKnowledgeEarlier);
        assertThat(all.creativePlans()).extracting(ProductAggregateView.CreativePlanView::creativePlanUuid)
                .containsExactly(archivedPlan, activePlan);
        assertThat(all.campaigns()).hasSize(3);
        assertThat(all.campaigns()).allSatisfy(item -> assertThat(item.association()).isNotNull());
        assertThat(all.assets()).extracting(ProductAggregateView.AssetView::assetUuid)
                .containsExactly(archivedAsset, activeAsset);
        assertThatThrownBy(() -> all.assets().getFirst().providerMetadata().put("unsafe", "mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void archivedProductRemainsReadableAndAggregateHasNoSideEffects() {
        Product product = createProduct("Archived aggregate");
        productCommands.archive(product.getProductUuid(), product.getVersion(), "archive-product");
        long auditBefore = count("audit_logs");
        long productVersion = jdbc.queryForObject("select version from products where product_uuid=?", Long.class,
                product.getProductUuid());

        ProductAggregateView result = service.get(product.getProductUuid(), false);

        assertThat(result.product().lifecycleStatus().name()).isEqualTo("ARCHIVED");
        assertThat(count("audit_logs")).isEqualTo(auditBefore);
        assertThat(jdbc.queryForObject("select version from products where product_uuid=?", Long.class,
                product.getProductUuid())).isEqualTo(productVersion);
    }

    @Test
    void queryCountIncludesQualityAndRemainsConstantWhenCardinalityIncreases() {
        Product product = createProduct("Query count");
        seedOneOfEach(product, 0);
        service.get(product.getProductUuid(), false);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        service.get(product.getProductUuid(), false);
        long initial = statistics.getPrepareStatementCount();

        for (int index = 1; index <= 4; index++) seedOneOfEach(product, index);
        statistics.clear();
        ProductAggregateView result = service.get(product.getProductUuid(), false);

        assertThat(initial).isEqualTo(9);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(9);
        assertThat(result.knowledge()).hasSize(5);
        assertThat(result.creativePlans()).hasSize(5);
        assertThat(result.campaigns()).hasSize(5);
        assertThat(result.assets()).hasSize(5);
        assertThat(result.quality()).isNotNull();
    }

    @Test
    void transactionContractIsReadOnlyRepeatableRead() throws Exception {
        Method method = ProductAggregateQueryService.class.getMethod("get", UUID.class, boolean.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void uuidAscendingBreaksEqualUpdatedAtTiesForEveryCollection() {
        Product product = createProduct("Stable ordering");
        Instant same = Instant.parse("2026-08-07T15:00:00Z");
        UUID knowledgeOne = knowledge(product, "One", false, same);
        UUID knowledgeTwo = knowledge(product, "Two", false, same);
        UUID planOne = creativePlan(product, "One", false, same);
        UUID planTwo = creativePlan(product, "Two", false, same);
        CampaignIds campaignOne = campaign(product, "One", false, false, same);
        CampaignIds campaignTwo = campaign(product, "Two", false, false, same);
        UUID assetOne = asset(product, planOne, campaignOne.campaignUuid(), "one.jpg", false, same);
        UUID assetTwo = asset(product, planTwo, campaignTwo.campaignUuid(), "two.jpg", false, same);

        ProductAggregateView result = service.get(product.getProductUuid(), false);

        assertThat(result.knowledge()).extracting(ProductAggregateView.KnowledgeView::knowledgeUuid)
                .containsExactlyElementsOf(sorted(knowledgeOne, knowledgeTwo));
        assertThat(result.creativePlans()).extracting(ProductAggregateView.CreativePlanView::creativePlanUuid)
                .containsExactlyElementsOf(sorted(planOne, planTwo));
        assertThat(result.campaigns()).extracting(ProductAggregateView.CampaignView::campaignUuid)
                .containsExactlyElementsOf(sorted(campaignOne.campaignUuid(), campaignTwo.campaignUuid()));
        assertThat(result.assets()).extracting(ProductAggregateView.AssetView::assetUuid)
                .containsExactlyElementsOf(sorted(assetOne, assetTwo));
    }

    private void seedOneOfEach(Product product, int index) {
        Instant timestamp = Instant.parse("2026-08-07T10:00:00Z").plusSeconds(index);
        knowledge(product, "Knowledge " + index, false, timestamp);
        UUID plan = creativePlan(product, "Plan " + index, false, timestamp);
        CampaignIds campaign = campaign(product, "Campaign " + index, false, false, timestamp);
        asset(product, plan, campaign.campaignUuid(), "asset-" + index + ".jpg", false, timestamp);
    }

    private Product createProduct(String name) {
        return productCommands.create(new CreateProductCommand(null, name, null, null, null, null,
                null, null, null, null, null), UUID.randomUUID().toString());
    }

    private UUID knowledge(Product product, String title, boolean archived, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into product_knowledge(knowledge_uuid,product_uuid,knowledge_type,title,content,lifecycle_status,archived_at,created_at,updated_at,version)
                values (?,?, 'FEATURE', ?, 'Content', ?, ?, ?, ?, 0)
                """, id, product.getProductUuid(), title, archived ? "ARCHIVED" : "ACTIVE",
                archived ? Timestamp.from(updatedAt) : null, Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return id;
    }

    private UUID creativePlan(Product product, String name, boolean archived, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into creative_plans(creative_plan_uuid,product_uuid,plan_name,lifecycle_status,archived_at,created_at,updated_at,version)
                values (?,?, ?, ?, ?, ?, ?, 0)
                """, id, product.getProductUuid(), name, archived ? "ARCHIVED" : "ACTIVE",
                archived ? Timestamp.from(updatedAt) : null, Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return id;
    }

    private CampaignIds campaign(Product product, String name, boolean campaignArchived,
            boolean associationArchived, Instant updatedAt) {
        UUID campaign = UUID.randomUUID();
        UUID association = UUID.randomUUID();
        jdbc.update("""
                insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,archived_at,created_at,updated_at,version)
                values (?,?, ?, ?, ?, ?, 0)
                """, campaign, name, campaignArchived ? "ARCHIVED" : "ACTIVE",
                campaignArchived ? Timestamp.from(updatedAt) : null,
                Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        jdbc.update("""
                insert into campaign_products(campaign_product_uuid,campaign_uuid,product_uuid,role,priority,budget_weight,lifecycle_status,archived_at,created_at,updated_at,version)
                values (?,?,?, 'PRIMARY', 1, 50.00, ?, ?, ?, ?, 0)
                """, association, campaign, product.getProductUuid(), associationArchived ? "ARCHIVED" : "ACTIVE",
                associationArchived ? Timestamp.from(updatedAt) : null,
                Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return new CampaignIds(campaign, association);
    }

    private UUID asset(Product product, UUID plan, UUID campaign, String filename,
            boolean archived, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into assets(asset_uuid,product_uuid,creative_plan_uuid,campaign_uuid,asset_type,original_filename,provider_metadata,lifecycle_status,archived_at,created_at,updated_at,version)
                values (?,?,?,?, 'IMAGE', ?, '{"region":"eu"}'::jsonb, ?, ?, ?, ?, 0)
                """, id, product.getProductUuid(), plan, campaign, filename,
                archived ? "ARCHIVED" : "ACTIVE", archived ? Timestamp.from(updatedAt) : null,
                Timestamp.from(updatedAt.minusSeconds(60)), Timestamp.from(updatedAt));
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private List<UUID> sorted(UUID... values) {
        return java.util.Arrays.stream(values).sorted(Comparator.comparing(UUID::toString)).toList();
    }

    private record CampaignIds(UUID campaignUuid, UUID associationUuid) {}
}
