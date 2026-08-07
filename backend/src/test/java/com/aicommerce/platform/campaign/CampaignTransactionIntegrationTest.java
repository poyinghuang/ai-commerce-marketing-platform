package com.aicommerce.platform.campaign;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.aicommerce.platform.audit.application.AuditWriter;
import com.aicommerce.platform.campaign.application.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CampaignTransactionIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

  @Autowired CampaignCommandService commands;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean AuditWriter audit;

  @Test
  void auditFailureRollsBackCampaignMutation() {
    doThrow(new IllegalStateException("audit failure")).when(audit).append(any());
    assertThatThrownBy(
            () ->
                commands.create(
                    new CreateCampaignCommand(
                        "Rollback", null, null, null, null, null, null, null, null, null, null),
                    "rollback-campaign"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from campaign_plans where campaign_name='Rollback'", Long.class))
        .isZero();
  }

  @Test
  void auditFailureRollsBackAssociationMutation() {
    UUID campaign = UUID.randomUUID(), product = UUID.randomUUID();
    jdbc.update(
        "insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values"
            + " (?,?,'ACTIVE',0)",
        campaign,
        "Campaign");
    jdbc.update(
        "insert into products(product_uuid,product_id,product_name,lifecycle_status,version) values"
            + " (?,?,?,'ACTIVE',0)",
        product,
        "PROD-00999999",
        "Product");
    doThrow(new IllegalStateException("audit failure")).when(audit).append(any());
    assertThatThrownBy(
            () ->
                commands.addProduct(
                    campaign,
                    new CreateCampaignProductCommand(product, null, null, null),
                    "rollback-association"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from campaign_products where campaign_uuid=? and product_uuid=?",
                Long.class,
                campaign,
                product))
        .isZero();
  }
}
