package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone4CSchemaIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void v14FunctionsAndNewCreateAdShapeArePresent() {
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success AND version='14'", String.class)).isEqualTo("14");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE proname IN ('is_stage4c_owned_operation','is_approved_stage4c_account','is_stage4c_new_create_ad','stage4c_create_ad_canonical_json')", Integer.class)).isEqualTo(4);
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

    @Test void v14ObjectsExistAfterColdLatestMigrateAndV1ThroughV13ChecksumsRemainStable() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname IN ('ct_platform_ad_submit_claim_evidence','ct_platform_ad_dispatch_result','ct_platform_ad_dispatch_result_entity','trg_platform_create_ad_new_shape')", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'platform_%'", Integer.class)).isEqualTo(10);
        assertThat(sha256("db/migration/V1__create_product_foundation.sql")).isEqualTo("ee4614654b5d47d1bebe40e451754413962d86b447d25354e1dc6cb70b03e2b9");
        assertThat(sha256("db/migration/V2__create_audit_foundation.sql")).isEqualTo("8f944bfdb655ca90cffa37215e5e7bc8134fb13e021e261acf399e080b78d243");
        assertThat(sha256("db/migration/V3__add_product_master_fields.sql")).isEqualTo("d1a5bc74fb4f2c57b711049fc6bb80e74dac1b7d5b4ff1905b95dec6ac204a93");
        assertThat(sha256("db/migration/V4__create_knowledge_plans_campaigns_assets.sql")).isEqualTo("d1b71029d5fa5cd9b283370281d57099495b3809e6c9fe43dc6afbc50f969ee9");
        assertThat(sha256("db/migration/V5__create_quality_and_workflow.sql")).isEqualTo("8bdf970eac44dbb14724dcda6ae1056439a2b241e53cf0988c737f00a89dee22");
        assertThat(sha256("db/migration/V6__create_sheet_import_foundation.sql")).isEqualTo("b8acba2394208517870bc105d651da2dfe003fbc18dc8b5869c46ea37515fe03");
        assertThat(sha256("db/migration/V6_1__add_sheet_import_header_presence.sql")).isEqualTo("4682d9dfbb9e194824064460242d81a665fc79bad6d41f5fb5059abf1fa18b67");
        assertThat(sha256("db/migration/V7__create_product_storage_folders.sql")).isEqualTo("74a0fc97fb1315a98336f54f7391e18011d53daffcace7b83805a910461d4cac");
        assertThat(sha256("db/migration/V8__create_ai_generation_foundation.sql")).isEqualTo("046d604295d83e94fba93fb54943fc832b3944ced5ebcc989ca475bb8bcef9f4");
        assertThat(sha256("db/migration/V9__create_ai_text_outputs.sql")).isEqualTo("7c7e14faae71394182ecca06010dd8b97f42598480530abfeb13ccacefca7367");
        assertThat(sha256("db/migration/V10__add_ai_image_outputs.sql")).isEqualTo("8d67fd339eb4cc0189e71394feb903a02bf51897fc0287bb2da1d8f78365f7d8");
        assertThat(sha256("db/migration/V11__create_ai_review_decisions.sql")).isEqualTo("761371c64dc2283c7ba3f644802d0b523a50ab5fe342e89da8c8c6b9befc0a1c");
        assertThat(sha256("db/migration/V12__create_platform_operation_foundation.sql")).isEqualTo("828be0d98a681501e0572ad038698002275f72fd66c0095b44def10da7ddfcf3");
        assertThat(sha256("db/migration/V13__add_platform_budget_authorization_ledger.sql")).isEqualTo("5078ef1c025b512bd4f99008c240122689d423e8b8188c09becc37c953f1497c");
    }

    @Test void mismatchedCreateAdSha256RollsBackAndJavaSqlCanonicalHashesAgree() throws Exception {
        UUID account = UUID.fromString("00000000-0000-4000-8000-00000000005b");
        UUID ad = UUID.randomUUID();
        String payload = payload(ad, true, "APPROVED_IMAGE_ASSET_V1");
        String sqlHash = jdbc.queryForObject("SELECT encode(sha256(convert_to(stage4c_create_ad_canonical_json(?::jsonb),'UTF8')),'hex')", String.class, payload);
        var canonicalizer = new com.aicommerce.platform.delivery.application.PlatformOperationInputCanonicalizer(new tools.jackson.databind.ObjectMapper());
        assertThat(canonicalizer.canonicalizeNewCreateAd(payload).sha256()).isEqualTo(sqlHash);
        String before = jdbc.queryForObject("SELECT count(*)::text FROM platform_operations", String.class);
        assertState("23514", () -> jdbc.update("""
                INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id)
                VALUES (?,?, 'CREATE_AD','AD',?,?, ?, ?::jsonb, ?,'LOCAL_ADMIN','local-admin','hash-mismatch')
                """, UUID.randomUUID(), account, ad, UUID.randomUUID(), hex(UUID.randomUUID()), payload, "a".repeat(64)));
        assertThat(jdbc.queryForObject("SELECT count(*)::text FROM platform_operations", String.class)).isEqualTo(before);
    }

    @Test void mutatedLocalTestFingerprintAndReferenceCannotChangeOwnership() {
        UUID test = UUID.fromString("00000000-0000-4000-8000-00000000005b");
        assertState("23514", () -> jdbc.update("UPDATE platform_accounts SET external_account_fingerprint=? WHERE platform_account_uuid=?", "b".repeat(64), test));
        assertState("23514", () -> jdbc.update("UPDATE platform_accounts SET account_reference='mutated-test' WHERE platform_account_uuid=?", test));
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

    private String sha256(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing " + resource);
            String canonical = new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        }
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
