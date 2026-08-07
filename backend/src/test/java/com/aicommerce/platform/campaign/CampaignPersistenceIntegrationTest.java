package com.aicommerce.platform.campaign;

import static org.assertj.core.api.Assertions.*;

import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.campaign.domain.*;
import com.aicommerce.platform.common.application.FieldPatch;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.Product;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CampaignPersistenceIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

  @Autowired CampaignCommandService commands;
  @Autowired CampaignQueryService queries;
  @Autowired ProductCommandService productCommands;
  @Autowired JdbcTemplate jdbc;

  @Test
  void provesCampaignAssociationLifecycleSearchAuditAndBoundaries() {
    Product one = productCommands.create(product("One"), "product-one"),
        two = productCommands.create(product("Two"), "product-two");
    CampaignPlan campaign = commands.create(campaign("Summer Launch"), "campaign-create");
    UUID campaignId = campaign.getCampaignUuid();
    assertAudit(
        "CAMPAIGN_PLAN",
        campaignId,
        "CREATE",
        "campaign-create",
        List.of(
            "campaign_name",
            "activity_type",
            "start_date",
            "end_date",
            "objective",
            "platform",
            "budget_daily",
            "budget_total",
            "currency",
            "promotion",
            "landing_page",
            "lifecycle_status"));
    assertValueTypes(
        "CAMPAIGN_PLAN",
        campaignId,
        "CREATE",
        Map.of(
            "start_date",
            "DATE",
            "end_date",
            "DATE",
            "budget_daily",
            "DECIMAL",
            "budget_total",
            "DECIMAL",
            "lifecycle_status",
            "ENUM"));
    CampaignProduct association =
        commands.addProduct(
            campaignId,
            new CreateCampaignProductCommand(
                one.getProductUuid(), "Hero", 1, new BigDecimal("60.00")),
            "association-create");
    assertAudit(
        "CAMPAIGN_PRODUCT",
        association.getCampaignProductUuid(),
        "CREATE",
        "association-create",
        List.of(
            "campaign_uuid",
            "product_uuid",
            "role",
            "priority",
            "budget_weight",
            "lifecycle_status"));
    assertValueTypes(
        "CAMPAIGN_PRODUCT",
        association.getCampaignProductUuid(),
        "CREATE",
        Map.of(
            "campaign_uuid",
            "UUID",
            "product_uuid",
            "UUID",
            "priority",
            "INTEGER",
            "budget_weight",
            "DECIMAL",
            "lifecycle_status",
            "ENUM"));
    assertThat(
            queries
                .list(
                    LifecycleStatus.ACTIVE,
                    null,
                    null,
                    null,
                    PageRequest.of(0, 10, Sort.by("campaignUuid")))
                .getContent())
        .extracting(CampaignPlan::getCampaignUuid)
        .contains(campaignId);
    long beforeDuplicate = count();
    assertThatThrownBy(
            () ->
                commands.addProduct(
                    campaignId,
                    new CreateCampaignProductCommand(one.getProductUuid(), null, null, null),
                    "duplicate"))
        .isInstanceOf(RelationshipConflictException.class);
    assertThat(count()).isEqualTo(beforeDuplicate);
    assertThat(
            queries
                .list(
                    LifecycleStatus.ACTIVE,
                    "summer",
                    one.getProductUuid(),
                    LifecycleStatus.ACTIVE,
                    PageRequest.of(0, 10, Sort.by("campaignName").and(Sort.by("campaignUuid"))))
                .getContent())
        .extracting(CampaignPlan::getCampaignUuid)
        .containsExactly(campaignId);
    var absent = FieldPatch.<String>absent();
    var dateAbsent = FieldPatch.<LocalDate>absent();
    var decimalAbsent = FieldPatch.<BigDecimal>absent();
    campaign =
        commands.patch(
            campaignId,
            0,
            new PatchCampaignCommand(
                absent,
                absent,
                dateAbsent,
                dateAbsent,
                FieldPatch.present("Updated"),
                absent,
                decimalAbsent,
                decimalAbsent,
                absent,
                absent,
                absent),
            "campaign-update");
    assertThat(campaign.getVersion()).isEqualTo(1);
    assertAudit("CAMPAIGN_PLAN", campaignId, "UPDATE", "campaign-update", List.of("objective"));
    long beforeStale = count();
    assertThatThrownBy(
            () ->
                commands.patch(
                    campaignId,
                    0,
                    new PatchCampaignCommand(
                        absent,
                        absent,
                        dateAbsent,
                        dateAbsent,
                        FieldPatch.present("Stale"),
                        absent,
                        decimalAbsent,
                        decimalAbsent,
                        absent,
                        absent,
                        absent),
                    "stale"))
        .isInstanceOf(CampaignPreconditionFailedException.class);
    assertThat(count()).isEqualTo(beforeStale);
    long beforeNoop = count();
    commands.patch(
        campaignId,
        1,
        new PatchCampaignCommand(
            absent,
            absent,
            dateAbsent,
            dateAbsent,
            FieldPatch.present("Updated"),
            absent,
            decimalAbsent,
            decimalAbsent,
            absent,
            absent,
            absent),
        "noop");
    assertThat(count()).isEqualTo(beforeNoop);
    association =
        commands.patchProduct(
            campaignId,
            one.getProductUuid(),
            0,
            new PatchCampaignProductCommand(
                FieldPatch.present("Primary"), FieldPatch.absent(), FieldPatch.absent()),
            "association-update");
    assertThat(association.getVersion()).isEqualTo(1);
    assertAudit(
        "CAMPAIGN_PRODUCT",
        association.getCampaignProductUuid(),
        "UPDATE",
        "association-update",
        List.of("role"));
    campaign = commands.archive(campaignId, 1, "campaign-archive");
    assertThat(campaign.getVersion()).isEqualTo(2);
    assertAudit(
        "CAMPAIGN_PLAN",
        campaignId,
        "ARCHIVE",
        "campaign-archive",
        List.of("lifecycle_status", "archived_at"));
    long afterCampaignArchive = count();
    commands.archive(campaignId, 2, "campaign-archive-noop");
    assertThat(count()).isEqualTo(afterCampaignArchive);
    assertThat(queries.getProduct(campaignId, one.getProductUuid()).getLifecycleStatus())
        .isEqualTo(LifecycleStatus.ACTIVE);
    assertThatThrownBy(
            () ->
                commands.patchProduct(
                    campaignId,
                    one.getProductUuid(),
                    1,
                    new PatchCampaignProductCommand(
                        FieldPatch.present("Blocked"), FieldPatch.absent(), FieldPatch.absent()),
                    "blocked"))
        .isInstanceOf(CampaignArchivedException.class);
    assertThat(count()).isEqualTo(afterCampaignArchive);
    campaign = commands.restore(campaignId, 2, "campaign-restore");
    assertAudit(
        "CAMPAIGN_PLAN",
        campaignId,
        "RESTORE",
        "campaign-restore",
        List.of("lifecycle_status", "archived_at"));
    long afterCampaignRestore = count();
    commands.restore(campaignId, 3, "campaign-restore-noop");
    assertThat(count()).isEqualTo(afterCampaignRestore);
    association =
        commands.archiveProduct(campaignId, one.getProductUuid(), 1, "association-archive");
    assertAudit(
        "CAMPAIGN_PRODUCT",
        association.getCampaignProductUuid(),
        "ARCHIVE",
        "association-archive",
        List.of("lifecycle_status", "archived_at"));
    long afterAssociationArchive = count();
    commands.archiveProduct(campaignId, one.getProductUuid(), 2, "association-archive-noop");
    assertThat(count()).isEqualTo(afterAssociationArchive);
    assertThat(
            queries
                .listProducts(
                    campaignId,
                    LifecycleStatus.ARCHIVED,
                    PageRequest.of(0, 10, Sort.by("campaignProductUuid")))
                .getTotalElements())
        .isEqualTo(1);
    commands.restoreProduct(campaignId, one.getProductUuid(), 2, "association-restore");
    assertAudit(
        "CAMPAIGN_PRODUCT",
        association.getCampaignProductUuid(),
        "RESTORE",
        "association-restore",
        List.of("lifecycle_status", "archived_at"));
    long afterAssociationRestore = count();
    commands.restoreProduct(campaignId, one.getProductUuid(), 3, "association-restore-noop");
    assertThat(count()).isEqualTo(afterAssociationRestore);
    CampaignProduct second =
        commands.addProduct(
            campaignId,
            new CreateCampaignProductCommand(
                two.getProductUuid(), "Support", 2, new BigDecimal("40.00")),
            "second-association");
    productCommands.archive(two.getProductUuid(), 0, "product-archive");
    assertThat(queries.getProduct(campaignId, two.getProductUuid()).getLifecycleStatus())
        .isEqualTo(LifecycleStatus.ACTIVE);
    assertThat(queries.getProduct(campaignId, two.getProductUuid()).getCampaignProductUuid())
        .isEqualTo(second.getCampaignProductUuid());
    assertThatThrownBy(
            () ->
                commands.patchProduct(
                    campaignId,
                    two.getProductUuid(),
                    0,
                    new PatchCampaignProductCommand(
                        FieldPatch.present("Blocked"), FieldPatch.absent(), FieldPatch.absent()),
                    "blocked-product"))
        .isInstanceOf(ProductArchivedException.class);
  }

  @Test
  void concurrentDuplicateAssociationProducesOneSuccessAndOneConflict() throws Exception {
    Product p = productCommands.create(product("Race"), "race-product");
    CampaignPlan c = commands.create(campaign("Race Campaign"), "race-campaign");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    AtomicInteger success = new AtomicInteger(), conflict = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      final int n = i;
      futures.add(
          pool.submit(
              () -> {
                ready.countDown();
                start.await();
                try {
                  commands.addProduct(
                      c.getCampaignUuid(),
                      new CreateCampaignProductCommand(p.getProductUuid(), "Role", n, null),
                      "race-" + n);
                  success.incrementAndGet();
                } catch (RelationshipConflictException expected) {
                  conflict.incrementAndGet();
                }
                return null;
              }));
    }
    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
    pool.shutdownNow();
    assertThat(success.get()).isEqualTo(1);
    assertThat(conflict.get()).isEqualTo(1);
    assertThat(
            queries
                .listProducts(
                    c.getCampaignUuid(),
                    null,
                    PageRequest.of(0, 10, Sort.by("campaignProductUuid")))
                .getTotalElements())
        .isEqualTo(1);
  }

  private CreateProductCommand product(String name) {
    return new CreateProductCommand(
        null, name, null, null, null, null, null, null, null, null, null);
  }

  private CreateCampaignCommand campaign(String name) {
    return new CreateCampaignCommand(
        name,
        "SALE",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        "Launch",
        "WEB",
        new BigDecimal("10.0000"),
        new BigDecimal("100.0000"),
        "USD",
        "Promo",
        "https://example.com");
  }

  private long count() {
    return jdbc.queryForObject(
        "select count(*) from audit_logs where entity_type in ('CAMPAIGN_PLAN','CAMPAIGN_PRODUCT')",
        Long.class);
  }

  private void assertAudit(
      String type, UUID id, String action, String requestId, List<String> fields) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "select actor_id,request_id from audit_logs where entity_type=? and entity_uuid=? and"
                + " action=?",
            type,
            id,
            action);
    assertThat(row).containsEntry("actor_id", "local-admin").containsEntry("request_id", requestId);
    List<Map<String, Object>> changes =
        jdbc.queryForList(
            "select c.field_name,c.change_order from audit_log_changes c join audit_logs l on"
                + " l.audit_uuid=c.audit_uuid where l.entity_type=? and l.entity_uuid=? and"
                + " l.action=? order by c.change_order",
            type,
            id,
            action);
    assertThat(changes).extracting(x -> x.get("field_name")).containsExactlyElementsOf(fields);
    for (int i = 0; i < changes.size(); i++)
      assertThat(((Number) changes.get(i).get("change_order")).intValue()).isEqualTo(i);
  }

  private void assertValueTypes(String type, UUID id, String action, Map<String, String> expected) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select c.field_name,c.value_type from audit_log_changes c join audit_logs l on"
                + " l.audit_uuid=c.audit_uuid where l.entity_type=? and l.entity_uuid=? and"
                + " l.action=?",
            type,
            id,
            action);
    Map<String, String> actual = new HashMap<>();
    rows.forEach(row -> actual.put((String) row.get("field_name"), (String) row.get("value_type")));
    expected.forEach((field, valueType) -> assertThat(actual).containsEntry(field, valueType));
  }
}
