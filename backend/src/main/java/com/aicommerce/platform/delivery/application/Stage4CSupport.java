package com.aicommerce.platform.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformOperation;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import com.aicommerce.platform.delivery.domain.PlatformStableErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class Stage4CSupport {
    static final String MAPPING = "APPROVED_IMAGE_ASSET_V1";
    static final List<Stage4CViews.Warning> WARNINGS = List.of(
            Stage4CViews.Warning.DETERMINISTIC_FAKE_ONLY,
            Stage4CViews.Warning.NO_REAL_PROVIDER_OR_SPEND,
            Stage4CViews.Warning.EVIDENCE_DIVERGENCE_BLOCKS_CREATE_OR_RESUME);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Stage4CSupport(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean sqlOwned(UUID operationUuid) {
        if (!sqlAvailable()) return false;
        Boolean owned = jdbc.queryForObject("SELECT is_stage4c_owned_operation(?)", Boolean.class, operationUuid);
        return Boolean.TRUE.equals(owned);
    }

    public boolean applicationOwned(PlatformOperation operation, UUID resolvedAccount) {
        return operation.getPlatformAccountUuid().equals(resolvedAccount)
                && approvedAccount(resolvedAccount)
                && sqlOwned(operation.getOperationUuid());
    }

    public boolean approvedAccount(UUID account) {
        if (!sqlAvailable()) return false;
        Boolean approved = jdbc.queryForObject("SELECT is_approved_stage4c_account(?)", Boolean.class, account);
        return Boolean.TRUE.equals(approved);
    }

    private boolean sqlAvailable() {
        Boolean exists = jdbc.queryForObject(
                "SELECT to_regprocedure('is_stage4c_owned_operation(uuid)') IS NOT NULL", Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void validateClaim(PlatformOperation operation) {
        Map<String, Object> payload = payload(operation.getRequestPayload());
        if (operation.getOperationType() == PlatformOperationType.CREATE_AD && payload.containsKey("expectedParentVersion")) {
            UUID adSet = UUID.fromString(payload.get("platformAdSetUuid").toString());
            long expected = ((Number) payload.get("expectedParentVersion")).longValue();
            Chain chain = lockCreate(operation.getPlatformAccountUuid(), adSet, operation.getEntityUuid(), true);
            if (chain.adSetVersion() != expected) throw stale();
            requireParents(chain, PlatformDesiredState.PAUSED);
            requireEvidence(chain, UUID.fromString(payload.get("productUuid").toString()),
                    UUID.fromString(payload.get("assetUuid").toString()),
                    UUID.fromString(payload.get("generationOutputUuid").toString()),
                    UUID.fromString(payload.get("reviewDecisionUuid").toString()),
                    payload.get("approvedChecksumSha256").toString());
            return;
        }
        if (!sqlOwned(operation.getOperationUuid())) return;
        if (operation.getOperationType() == PlatformOperationType.RESUME) {
            Chain chain = lockAd(operation.getPlatformAccountUuid(), operation.getEntityUuid(), true);
            long expected = ((Number) payload.get("expectedEntityVersion")).longValue();
            if (chain.adVersion() != expected) throw stale();
            if (chain.adDesired() != PlatformDesiredState.PAUSED || chain.adExternal() == null) {
                throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE, java.util.Optional.of(operation.getOperationUuid()));
            }
            requireParents(chain, PlatformDesiredState.ACTIVE);
            requireCurrentEvidence(chain, true);
            return;
        }
        if (operation.getOperationType() == PlatformOperationType.PAUSE) {
            Chain chain = lockAd(operation.getPlatformAccountUuid(), operation.getEntityUuid(), true);
            long expected = ((Number) payload.get("expectedEntityVersion")).longValue();
            if (chain.adVersion() != expected) throw stale();
            if (chain.adDesired() != PlatformDesiredState.ACTIVE || chain.adExternal() == null) {
                throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_INVALID_OPERATION_STATE, java.util.Optional.of(operation.getOperationUuid()));
            }
        }
    }

    public Chain lockCreate(UUID account, UUID adSet, UUID ad, boolean lock) {
        Chain parents = lockParents(account, adSet, lock);
        if (lock) {
            List<Integer> locked = jdbc.query(
                    "SELECT 1 FROM platform_ads WHERE platform_ad_uuid=? AND platform_account_uuid=? FOR UPDATE",
                    (rs, n) -> 1, ad, account);
            if (locked.size() != 1) throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
        List<Chain> ads = jdbc.query("""
          SELECT a.platform_ad_uuid,a.product_uuid,a.asset_uuid,a.generation_output_uuid,a.review_decision_uuid,
                 a.approved_checksum_sha256,a.creative_mapping_key,a.desired_state,a.external_id,a.version
          FROM platform_ads a WHERE a.platform_ad_uuid=? AND a.platform_account_uuid=?
          """,
                (rs, n) -> parents.withAd(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        PlatformDesiredState.valueOf(rs.getString(8)), rs.getString(9), rs.getLong(10)), ad, account);
        if (ads.size() != 1) throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
        return ads.getFirst();
    }

    public Chain lockParents(UUID account, UUID adSet, boolean lock) {
        lockAccount(account, lock);
        if (lock) {
            List<UUID> campaigns = jdbc.query(
                    "SELECT platform_campaign_uuid FROM platform_ad_sets WHERE platform_ad_set_uuid=? AND platform_account_uuid=?",
                    (rs, n) -> rs.getObject(1, UUID.class), adSet, account);
            if (campaigns.size() != 1) throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
            if (jdbc.query("SELECT 1 FROM platform_campaigns WHERE platform_campaign_uuid=? AND platform_account_uuid=? FOR UPDATE",
                    (rs, n) -> 1, campaigns.getFirst(), account).size() != 1) {
                throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
            if (jdbc.query("SELECT 1 FROM platform_ad_sets WHERE platform_ad_set_uuid=? AND platform_account_uuid=? FOR UPDATE",
                    (rs, n) -> 1, adSet, account).size() != 1) {
                throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
        }
        List<Chain> rows = jdbc.query("""
          SELECT s.platform_ad_set_uuid,s.desired_state,s.external_id,s.version,
                 c.platform_campaign_uuid,c.desired_state,c.external_id,c.version
          FROM platform_campaigns c
          JOIN platform_ad_sets s ON s.platform_campaign_uuid=c.platform_campaign_uuid AND s.platform_account_uuid=c.platform_account_uuid
          WHERE s.platform_ad_set_uuid=? AND s.platform_account_uuid=?
          """, (rs, n) -> new Chain(
                rs.getObject(1, UUID.class), PlatformDesiredState.valueOf(rs.getString(2)), rs.getString(3), rs.getLong(4),
                rs.getObject(5, UUID.class), PlatformDesiredState.valueOf(rs.getString(6)), rs.getString(7), rs.getLong(8),
                null, null, null, null, null, null, null, null, null, 0), adSet, account);
        if (rows.size() != 1) throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    public Chain lockAd(UUID account, UUID ad, boolean lock) {
        lockAccount(account, lock);
        if (lock) {
            List<UUID[]> parents = jdbc.query("""
              SELECT s.platform_campaign_uuid,s.platform_ad_set_uuid
              FROM platform_ads a
              JOIN platform_ad_sets s ON s.platform_ad_set_uuid=a.platform_ad_set_uuid AND s.platform_account_uuid=a.platform_account_uuid
              WHERE a.platform_ad_uuid=? AND a.platform_account_uuid=?
              """, (rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, ad, account);
            if (parents.size() != 1) throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
            UUID campaignId = parents.getFirst()[0];
            UUID adSetId = parents.getFirst()[1];
            if (jdbc.query("SELECT 1 FROM platform_campaigns WHERE platform_campaign_uuid=? AND platform_account_uuid=? FOR UPDATE",
                    (rs, n) -> 1, campaignId, account).size() != 1
                    || jdbc.query("SELECT 1 FROM platform_ad_sets WHERE platform_ad_set_uuid=? AND platform_account_uuid=? FOR UPDATE",
                            (rs, n) -> 1, adSetId, account).size() != 1
                    || jdbc.query("SELECT 1 FROM platform_ads WHERE platform_ad_uuid=? AND platform_account_uuid=? FOR UPDATE",
                            (rs, n) -> 1, ad, account).size() != 1) {
                throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
        }
        List<Chain> rows = jdbc.query("""
          SELECT s.platform_ad_set_uuid,s.desired_state,s.external_id,s.version,
                 c.platform_campaign_uuid,c.desired_state,c.external_id,c.version,
                 a.platform_ad_uuid,a.product_uuid,a.asset_uuid,a.generation_output_uuid,a.review_decision_uuid,
                 a.approved_checksum_sha256,a.creative_mapping_key,a.desired_state,a.external_id,a.version
          FROM platform_campaigns c
          JOIN platform_ad_sets s ON s.platform_campaign_uuid=c.platform_campaign_uuid AND s.platform_account_uuid=c.platform_account_uuid
          JOIN platform_ads a ON a.platform_ad_set_uuid=s.platform_ad_set_uuid AND a.platform_account_uuid=s.platform_account_uuid
          WHERE a.platform_ad_uuid=? AND a.platform_account_uuid=?
          """, (rs, n) -> new Chain(
                rs.getObject(1, UUID.class), PlatformDesiredState.valueOf(rs.getString(2)), rs.getString(3), rs.getLong(4),
                rs.getObject(5, UUID.class), PlatformDesiredState.valueOf(rs.getString(6)), rs.getString(7), rs.getLong(8),
                rs.getObject(9, UUID.class), rs.getObject(10, UUID.class), rs.getObject(11, UUID.class),
                rs.getObject(12, UUID.class), rs.getObject(13, UUID.class), rs.getString(14), rs.getString(15),
                PlatformDesiredState.valueOf(rs.getString(16)), rs.getString(17), rs.getLong(18)), ad, account);
        if (rows.size() != 1) throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    public void requireParents(Chain chain, PlatformDesiredState required) {
        if (chain.campaignDesired() != required || chain.adSetDesired() != required
                || chain.campaignExternal() == null || chain.campaignExternal().isBlank()
                || chain.adSetExternal() == null || chain.adSetExternal().isBlank()) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_PARENT_STATE_INVALID, java.util.Optional.empty());
        }
    }

    public void requireEvidence(Chain chain, UUID product, UUID asset, UUID output, UUID review, String checksum) {
        if (chain.productUuid() == null || !chain.productUuid().equals(product) || !chain.assetUuid().equals(asset)
                || !chain.outputUuid().equals(output) || !chain.reviewUuid().equals(review)
                || !checksum.equals(chain.checksum()) || !MAPPING.equals(chain.mapping())) {
            throw evidence();
        }
        requireCurrentEvidence(chain, true);
    }

    public String requireCurrentEvidence(Chain chain) {
        return requireCurrentEvidence(chain, true);
    }

    public String requireCurrentEvidence(Chain chain, boolean lock) {
        List<String> checksums = jdbc.query("""
          SELECT a.checksum_sha256 FROM products p
            JOIN assets a ON a.asset_uuid=? AND a.product_uuid=p.product_uuid
            JOIN ai_generation_outputs o ON o.generation_output_uuid=? AND o.product_uuid=p.product_uuid
            JOIN ai_review_decisions d ON d.review_decision_uuid=? AND d.generation_output_uuid=o.generation_output_uuid
          WHERE p.product_uuid=? AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE'
            AND a.checksum_sha256 ~ '^[0-9a-f]{64}$' AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid
            AND o.review_status='APPROVED' AND o.preservation_status='PASSED' AND o.output_checksum_sha256=a.checksum_sha256
            AND o.output_checksum_sha256=? AND d.decision='APPROVED'
          """ + (lock ? " FOR UPDATE OF p,a,o,d" : ""),
                (rs, n) -> rs.getString(1), chain.assetUuid(), chain.outputUuid(), chain.reviewUuid(), chain.productUuid(), chain.checksum());
        if (checksums.size() != 1) throw evidence();
        return checksums.getFirst();
    }

    public String lookupChecksum(UUID product, UUID asset, UUID output, UUID review, boolean lock) {
        List<String> checksums = jdbc.query("""
          SELECT a.checksum_sha256 FROM products p
            JOIN assets a ON a.asset_uuid=? AND a.product_uuid=p.product_uuid
            JOIN ai_generation_outputs o ON o.generation_output_uuid=? AND o.product_uuid=p.product_uuid
            JOIN ai_review_decisions d ON d.review_decision_uuid=? AND d.generation_output_uuid=o.generation_output_uuid
          WHERE p.product_uuid=? AND p.lifecycle_status='ACTIVE' AND a.asset_type='IMAGE' AND a.lifecycle_status='ACTIVE'
            AND a.checksum_sha256 ~ '^[0-9a-f]{64}$' AND o.generation_type='IMAGE' AND o.generated_asset_uuid=a.asset_uuid
            AND o.review_status='APPROVED' AND o.preservation_status='PASSED' AND o.output_checksum_sha256=a.checksum_sha256
            AND d.decision='APPROVED'
          """ + (lock ? " FOR UPDATE OF p,a,o,d" : ""), (rs, n) -> rs.getString(1), asset, output, review, product);
        if (checksums.size() != 1) throw evidence();
        return checksums.getFirst();
    }

    public Stage4CViews.Ad readAd(UUID account, UUID ad) {
        List<Stage4CViews.Ad> rows = jdbc.query("""
          SELECT platform_ad_uuid,platform_ad_set_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,
                 approved_checksum_sha256,creative_mapping_key,desired_state,observed_state,external_id,created_at,updated_at,version
          FROM platform_ads WHERE platform_ad_uuid=? AND platform_account_uuid=?
          """, (rs, n) -> new Stage4CViews.Ad(
                rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                checksumFingerprint(rs.getString(7)), rs.getString(8), PlatformDesiredState.valueOf(rs.getString(9)),
                Optional.ofNullable(rs.getString(10)).map(com.aicommerce.platform.delivery.domain.PlatformObservedState::valueOf),
                Optional.ofNullable(rs.getString(11)).map(Stage4CSupport::externalFingerprint),
                rs.getTimestamp(12).toInstant(), rs.getTimestamp(13).toInstant(), rs.getLong(14)), ad, account);
        if (rows.size() != 1) throw new Stage4BException("PLATFORM_AD_NOT_FOUND", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private void lockAccount(UUID account, boolean lock) {
        if (!lock) return;
        List<Integer> locked = jdbc.query(
                "SELECT 1 FROM platform_accounts WHERE platform_account_uuid=? FOR UPDATE", (rs, n) -> 1, account);
        if (locked.size() != 1) throw new Stage4BException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static String checksumFingerprint(String checksum) {
        return sha256("stage4c-approved-checksum-v1\n" + checksum);
    }

    public static String externalFingerprint(String externalId) {
        return sha256(externalId);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception exception) {
            throw new PlatformOperationException(PlatformStableErrorCode.PLATFORM_CONTRACT_INVALID, java.util.Optional.empty());
        }
    }

    private static PlatformOperationException evidence() {
        return new PlatformOperationException(PlatformStableErrorCode.PLATFORM_EVIDENCE_INVALID, java.util.Optional.empty());
    }

    private static PlatformOperationException stale() {
        return new PlatformOperationException(PlatformStableErrorCode.PLATFORM_STALE_VERSION, java.util.Optional.empty());
    }

    public record Chain(
            UUID adSetUuid, PlatformDesiredState adSetDesired, String adSetExternal, long adSetVersion,
            UUID campaignUuid, PlatformDesiredState campaignDesired, String campaignExternal, long campaignVersion,
            UUID adUuid, UUID productUuid, UUID assetUuid, UUID outputUuid, UUID reviewUuid,
            String checksum, String mapping, PlatformDesiredState adDesired, String adExternal, long adVersion) {
        Chain withAd(UUID ad, UUID product, UUID asset, UUID output, UUID review, String checksum, String mapping,
                PlatformDesiredState desired, String external, long version) {
            return new Chain(adSetUuid, adSetDesired, adSetExternal, adSetVersion, campaignUuid, campaignDesired,
                    campaignExternal, campaignVersion, ad, product, asset, output, review, checksum, mapping, desired, external, version);
        }
    }
}
