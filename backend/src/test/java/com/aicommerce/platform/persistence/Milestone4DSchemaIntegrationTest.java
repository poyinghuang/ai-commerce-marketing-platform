package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone4DSchemaIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v15AddsExactlyTheThreeAsOfIndexes() {
        assertThat(jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success AND version='15'", String.class)).isEqualTo("15");
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                WHERE schemaname='public' AND indexname IN (
                  'idx_platform_metrics_campaign_as_of','idx_platform_metrics_ad_set_as_of','idx_platform_metrics_ad_as_of')
                ORDER BY indexname
                """, String.class)).containsExactly(
                "idx_platform_metrics_ad_as_of",
                "idx_platform_metrics_ad_set_as_of",
                "idx_platform_metrics_campaign_as_of");
        assertThat(jdbc.queryForObject(
                "SELECT pg_get_functiondef(oid) FROM pg_proc WHERE proname='verify_platform_entity_operation_coherence'",
                String.class)).contains("NEW.observed_state IS DISTINCT FROM OLD.observed_state");
    }
}
