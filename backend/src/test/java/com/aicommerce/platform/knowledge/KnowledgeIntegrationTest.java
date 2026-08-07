package com.aicommerce.platform.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.application.CreateKnowledgeCommand;
import com.aicommerce.platform.knowledge.application.KnowledgeArchivedException;
import com.aicommerce.platform.knowledge.application.KnowledgeCommandService;
import com.aicommerce.platform.knowledge.application.KnowledgeNotFoundException;
import com.aicommerce.platform.knowledge.application.KnowledgePreconditionFailedException;
import com.aicommerce.platform.knowledge.application.KnowledgeQueryService;
import com.aicommerce.platform.knowledge.application.PatchKnowledgeCommand;
import com.aicommerce.platform.knowledge.domain.KnowledgeType;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired
    ProductCommandService products;

    @Autowired
    KnowledgeCommandService commands;

    @Autowired
    KnowledgeQueryService queries;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lifecycleConcurrencyOwnershipAndEveryAuditActionAreTransactional() {
        Product product = product();
        long before = auditCount();

        ProductKnowledge knowledge = commands.create(
                product.getProductUuid(),
                new CreateKnowledgeCommand(KnowledgeType.FEATURE, "Title", "Content", "Source"),
                "knowledge-create");
        assertThat(knowledge.getVersion()).isZero();
        assertAudit(
                knowledge.getKnowledgeUuid(),
                "CREATE",
                "knowledge-create",
                List.of("knowledge_type", "title", "content", "source", "lifecycle_status"));

        ProductKnowledge updated = commands.patch(
                product.getProductUuid(),
                knowledge.getKnowledgeUuid(),
                0,
                new PatchKnowledgeCommand(
                        FieldPatch.absent(),
                        FieldPatch.present("Updated"),
                        FieldPatch.absent(),
                        FieldPatch.absent()),
                "knowledge-update");
        assertThat(updated.getVersion()).isEqualTo(1);
        assertAudit(knowledge.getKnowledgeUuid(), "UPDATE", "knowledge-update", List.of("title"));

        assertThatThrownBy(() -> commands.patch(
                product.getProductUuid(),
                knowledge.getKnowledgeUuid(),
                0,
                new PatchKnowledgeCommand(
                        FieldPatch.absent(),
                        FieldPatch.present("Stale"),
                        FieldPatch.absent(),
                        FieldPatch.absent()),
                "stale"))
                .isInstanceOf(KnowledgePreconditionFailedException.class);
        assertThat(auditCount()).isEqualTo(before + 2);

        ProductKnowledge archived = commands.archive(
                product.getProductUuid(), knowledge.getKnowledgeUuid(), 1, "knowledge-archive");
        assertThat(archived.getVersion()).isEqualTo(2);
        assertAudit(
                knowledge.getKnowledgeUuid(),
                "ARCHIVE",
                "knowledge-archive",
                List.of("lifecycle_status", "archived_at"));

        assertThatThrownBy(() -> commands.patch(
                product.getProductUuid(),
                knowledge.getKnowledgeUuid(),
                2,
                new PatchKnowledgeCommand(
                        FieldPatch.absent(),
                        FieldPatch.present("Blocked"),
                        FieldPatch.absent(),
                        FieldPatch.absent()),
                "archived-patch"))
                .isInstanceOf(KnowledgeArchivedException.class);
        commands.archive(product.getProductUuid(), knowledge.getKnowledgeUuid(), 2, "archive-noop");
        assertThat(auditCount()).isEqualTo(before + 3);

        ProductKnowledge restored = commands.restore(
                product.getProductUuid(), knowledge.getKnowledgeUuid(), 2, "knowledge-restore");
        assertThat(restored.getVersion()).isEqualTo(3);
        assertAudit(
                knowledge.getKnowledgeUuid(),
                "RESTORE",
                "knowledge-restore",
                List.of("lifecycle_status", "archived_at"));

        ProductKnowledge noChange = commands.patch(
                product.getProductUuid(),
                knowledge.getKnowledgeUuid(),
                3,
                new PatchKnowledgeCommand(
                        FieldPatch.absent(),
                        FieldPatch.present("Updated"),
                        FieldPatch.absent(),
                        FieldPatch.absent()),
                "no-change");
        assertThat(noChange.getVersion()).isEqualTo(3);
        assertThat(auditCount()).isEqualTo(before + 4);

        Product otherProduct = product();
        assertThatThrownBy(() -> queries.get(otherProduct.getProductUuid(), knowledge.getKnowledgeUuid()))
                .isInstanceOf(KnowledgeNotFoundException.class);
    }

    @Test
    void activeArchivedAndAllQueriesProvideStablePagination() {
        Product product = product();
        List<ProductKnowledge> created = List.of(
                knowledge(product, "Same title", KnowledgeType.FEATURE),
                knowledge(product, "Same title", KnowledgeType.BENEFIT),
                knowledge(product, "Same title", KnowledgeType.FAQ),
                knowledge(product, "Same title", KnowledgeType.PROOF));
        commands.archive(product.getProductUuid(), created.get(3).getKnowledgeUuid(), 0, "archive-page-item");

        Sort stableSort = Sort.by("title").ascending().and(Sort.by("knowledgeUuid").ascending());
        Page<ProductKnowledge> activePageZero = queries.list(
                product.getProductUuid(), LifecycleStatus.ACTIVE, PageRequest.of(0, 2, stableSort));
        Page<ProductKnowledge> activePageOne = queries.list(
                product.getProductUuid(), LifecycleStatus.ACTIVE, PageRequest.of(1, 2, stableSort));
        Page<ProductKnowledge> archived = queries.list(
                product.getProductUuid(), LifecycleStatus.ARCHIVED, PageRequest.of(0, 10, stableSort));
        Page<ProductKnowledge> all = queries.list(
                product.getProductUuid(), null, PageRequest.of(0, 10, stableSort));

        assertThat(activePageZero.getTotalElements()).isEqualTo(3);
        assertThat(activePageZero.getContent()).hasSize(2);
        assertThat(activePageOne.getContent()).hasSize(1);
        assertThat(activePageZero.getContent())
                .extracting(ProductKnowledge::getKnowledgeUuid)
                .doesNotContainAnyElementsOf(
                        activePageOne.getContent().stream().map(ProductKnowledge::getKnowledgeUuid).toList());
        assertThat(archived.getTotalElements()).isEqualTo(1);
        assertThat(archived.getContent().getFirst().getKnowledgeUuid())
                .isEqualTo(created.get(3).getKnowledgeUuid());
        assertThat(all.getTotalElements()).isEqualTo(4);
    }

    @Test
    void archivedProductIsReadableButBlocksMutation() {
        Product product = product();
        ProductKnowledge knowledge = commands.create(
                product.getProductUuid(),
                new CreateKnowledgeCommand(KnowledgeType.FAQ, "Question", "Answer", null),
                "create");
        products.archive(product.getProductUuid(), product.getVersion(), "product-archive");

        assertThat(queries.get(product.getProductUuid(), knowledge.getKnowledgeUuid())).isNotNull();
        long before = auditCount();
        assertThatThrownBy(() -> commands.patch(
                product.getProductUuid(),
                knowledge.getKnowledgeUuid(),
                0,
                new PatchKnowledgeCommand(
                        FieldPatch.absent(),
                        FieldPatch.present("Blocked"),
                        FieldPatch.absent(),
                        FieldPatch.absent()),
                "blocked"))
                .isInstanceOf(ProductArchivedException.class);
        assertThat(auditCount()).isEqualTo(before);
    }

    private ProductKnowledge knowledge(Product product, String title, KnowledgeType type) {
        return commands.create(
                product.getProductUuid(),
                new CreateKnowledgeCommand(type, title, "Content for " + type, null),
                "create-" + type.name().toLowerCase());
    }

    private Product product() {
        return products.create(
                new CreateProductCommand(
                        null, "Knowledge Product", null, null, null, null, null, null, null, null, null),
                "product-create");
    }

    private void assertAudit(UUID entityUuid, String action, String requestId, List<String> changedFields) {
        Map<String, Object> audit = jdbc.queryForMap(
                "select action, actor_id, request_id from audit_logs where entity_uuid = ? and action = ?",
                entityUuid,
                action);
        assertThat(audit)
                .containsEntry("action", action)
                .containsEntry("actor_id", "local-admin")
                .containsEntry("request_id", requestId);
        assertThat(jdbc.queryForList(
                """
                select c.field_name
                  from audit_log_changes c
                  join audit_logs l on l.audit_uuid = c.audit_uuid
                 where l.entity_uuid = ? and l.action = ?
                 order by c.change_order
                """,
                String.class,
                entityUuid,
                action))
                .containsExactlyElementsOf(changedFields);
    }

    private long auditCount() {
        return jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type='PRODUCT_KNOWLEDGE'", Long.class);
    }
}
