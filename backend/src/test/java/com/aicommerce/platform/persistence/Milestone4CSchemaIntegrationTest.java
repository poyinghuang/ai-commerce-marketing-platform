package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

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
class Milestone4CSchemaIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc;

    @Test void v14FunctionsAndNewCreateAdShapeArePresent() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success AND version='14'", String.class)).isEqualTo("14");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE proname IN ('is_stage4c_owned_operation','is_approved_stage4c_account','is_stage4c_new_create_ad')", Integer.class)).isEqualTo(3);
        UUID testAccount = UUID.fromString("00000000-0000-4000-8000-00000000005b");
        assertThat(jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, testAccount)).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, UUID.randomUUID())).isFalse();
        jdbc.execute("SELECT set_config('application_name','forged-owner',true)");
        jdbc.execute("SELECT set_config('ai.account','" + testAccount + "',true)");
        assertThat(jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, UUID.randomUUID())).isFalse();
    }

    @Test void newCreateAdInsertRequiresParentVersionAndApprovedMapping() {
        UUID account = UUID.fromString("00000000-0000-4000-8000-00000000005b");
        UUID ad = UUID.randomUUID();
        String legacy = payload(ad, false, "IMAGE_PRIMARY_V1");
        assertState("23514", () -> jdbc.update("""
                INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id)
                VALUES (?,?, 'CREATE_AD','AD',?,?, ?, ?::jsonb, ?,'LOCAL_ADMIN','local-admin','direct-sql')
                """, UUID.randomUUID(), account, ad, UUID.randomUUID(), hex(UUID.randomUUID()), legacy, hex(UUID.randomUUID())));
        String jsonNormalized = payload(ad, true, "APPROVED_IMAGE_ASSET_V1").replace("\"expectedParentVersion\":1", "\"expectedParentVersion\":1e0");
        Boolean valid = jdbc.queryForObject("SELECT is_valid_platform_request(?::jsonb,'CREATE_AD','AD',?)", Boolean.class, jsonNormalized, ad);
        assertThat(valid).isTrue();
    }

    @Test void ownershipIsFalseForUuidOnlyForgedAccount() {
        UUID forged = UUID.randomUUID();
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",
                forged, "forged-"+forged, forged.toString().replace("-","")+"c".repeat(32));
        assertThat(jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, forged)).isFalse();
        UUID test = UUID.fromString("00000000-0000-4000-8000-00000000005b");
        assertThat(jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, test)).isTrue();
    }

    private static String payload(UUID ad, boolean parent, String mapping) {
        String keys = "\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\"" + ad
                + "\",\"platformAdUuid\":\"" + ad + "\",\"platformAdSetUuid\":\"00000000-0000-4000-8000-0000000000a1\""
                + (parent ? ",\"expectedParentVersion\":1" : "")
                + ",\"productUuid\":\"00000000-0000-4000-8000-0000000000a2\",\"assetUuid\":\"00000000-0000-4000-8000-0000000000a3\""
                + ",\"generationOutputUuid\":\"00000000-0000-4000-8000-0000000000a4\",\"reviewDecisionUuid\":\"00000000-0000-4000-8000-0000000000a5\""
                + ",\"approvedChecksumSha256\":\"" + "a".repeat(64) + "\",\"creativeMappingKey\":\"" + mapping + "\",\"desiredState\":\"PAUSED\"";
        return "{" + keys + "}";
    }

    private static String hex(UUID id) {
        return id.toString().replace("-", "").repeat(2);
    }

    private static void assertState(String state, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).satisfies(failure -> {
            Throwable current = failure;
            while (current.getCause() != null) current = current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);
            assertThat(((SQLException) current).getSQLState()).isEqualTo(state);
        });
    }
}
