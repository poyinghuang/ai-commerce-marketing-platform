package com.aicommerce.platform.delivery.application;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true'"
        + " && '${platform.stage4b.enabled:false}' == 'true' && '${platform.stage8.insights.live:false}' == 'true'")
public final class Stage8CAccountInitializer implements ApplicationRunner {
    public static final UUID LOCAL_UUID = UUID.fromString("00000000-0000-4000-8000-00000000008c");
    public static final UUID TEST_UUID = UUID.fromString("00000000-0000-4000-8000-00000000008d");
    public static final UUID LOCAL_PLAN_UUID = UUID.fromString("00000000-0000-4000-8000-00000000038c");
    public static final UUID TEST_PLAN_UUID = UUID.fromString("00000000-0000-4000-8000-00000000038d");
    public static final UUID LOCAL_CAMPAIGN_UUID = UUID.fromString("00000000-0000-4000-8000-00000000018c");
    public static final UUID TEST_CAMPAIGN_UUID = UUID.fromString("00000000-0000-4000-8000-00000000018d");
    public static final UUID LOCAL_AD_SET_UUID = UUID.fromString("00000000-0000-4000-8000-00000000028c");
    public static final UUID TEST_AD_SET_UUID = UUID.fromString("00000000-0000-4000-8000-00000000028d");
    public static final String LOCAL_FINGERPRINT = "f635ad6e3413a36d77a5310c48f0139b28ceaeb64e4109a7f2e977895d64e111";
    public static final String TEST_FINGERPRINT = "c8d2c90e9f0230a9b380954b29cee49c066b312785340409cdca53b1191921be";

    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final String campaignExternalId;
    private final String adSetExternalId;
    private final String adExternalId;

    public Stage8CAccountInitializer(JdbcTemplate jdbc, Environment environment,
            @Value("${META_TEST_CAMPAIGN_ID:}") String campaignExternalId,
            @Value("${META_TEST_ADSET_ID:}") String adSetExternalId,
            @Value("${META_TEST_AD_ID:}") String adExternalId) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.campaignExternalId = campaignExternalId;
        this.adSetExternalId = adSetExternalId;
        this.adExternalId = adExternalId;
    }

    @Override
    public void run(ApplicationArguments args) {
        Boolean allowed = jdbc.queryForObject("""
          SELECT EXISTS (
            SELECT 1 FROM pg_constraint c
            JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace
            WHERE n.nspname=current_schema() AND r.relname='platform_accounts'
              AND pg_get_constraintdef(c.oid) LIKE '%META%'
          )
          """, Boolean.class);
        if (!Boolean.TRUE.equals(allowed)) {
            return;
        }
        boolean test = Arrays.asList(environment.getActiveProfiles()).contains("test");
        UUID account = test ? TEST_UUID : LOCAL_UUID;
        String ref = test ? "stage8c-meta-test" : "stage8c-meta-local";
        String env = test ? "TEST" : "LOCAL";
        String fp = test ? TEST_FINGERPRINT : LOCAL_FINGERPRINT;
        jdbc.update("""
          INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,
            external_account_fingerprint,currency,timezone,lifecycle_status,created_at,updated_at,version)
          VALUES (?,'META',?,?,?,'TWD','Asia/Taipei','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
          ON CONFLICT DO NOTHING
          """, account, env, ref, fp);
        Integer candidates = jdbc.queryForObject(
                "SELECT count(*) FROM platform_accounts WHERE provider_key='META' AND account_reference=?",
                Integer.class, ref);
        Integer exact = jdbc.queryForObject("""
          SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='META'
            AND environment=? AND account_reference=? AND external_account_fingerprint=?
            AND currency='TWD' AND timezone='Asia/Taipei' AND lifecycle_status='ACTIVE' AND archived_at IS NULL
          """, Integer.class, account, env, ref, fp);
        if (candidates == null || candidates != 1 || exact == null || exact != 1) {
            throw new IllegalStateException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID");
        }
        UUID plan = test ? TEST_PLAN_UUID : LOCAL_PLAN_UUID;
        UUID campaign = test ? TEST_CAMPAIGN_UUID : LOCAL_CAMPAIGN_UUID;
        UUID adSet = test ? TEST_AD_SET_UUID : LOCAL_AD_SET_UUID;
        jdbc.update("""
          INSERT INTO campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version)
          VALUES (?,'Stage 08C META','ACTIVE',0)
          ON CONFLICT DO NOTHING
          """, plan);
        jdbc.update("""
          INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,
            desired_state,account_timezone)
          VALUES (?,?,?,'OUTCOME_SALES','PAUSED','Asia/Taipei')
          ON CONFLICT DO NOTHING
          """, campaign, plan, account);
        jdbc.update("""
          INSERT INTO platform_ad_sets(platform_ad_set_uuid,platform_campaign_uuid,platform_account_uuid,budget_type,
            budget_amount,currency,account_timezone,optimization_goal,targeting_profile_key,placement_profile_key,
            desired_state)
          VALUES (?,?,?,'DAILY',10.000000,'TWD','Asia/Taipei','OFFSITE_CONVERSIONS','TW_BROAD_FEEDS_V1',
            'TW_BROAD_FEEDS_V1','PAUSED')
          ON CONFLICT DO NOTHING
          """, adSet, campaign, account);
        externalId(campaignExternalId);
        externalId(adSetExternalId);
        externalId(adExternalId);
    }

    private static String externalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.matches("^[A-Za-z0-9._:-]{1,128}$") ? value : null;
    }
}
