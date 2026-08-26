package com.aicommerce.platform.delivery.application;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage4b.enabled:false}' == 'true'")
public final class Stage7C1AccountInitializer implements ApplicationRunner {
    public static final UUID LOCAL_UUID=UUID.fromString("00000000-0000-4000-8000-00000000007c");
    public static final UUID TEST_UUID=UUID.fromString("00000000-0000-4000-8000-00000000007d");
    static final String LOCAL_FINGERPRINT="1a7fa62f6a1005a7605fa034354b13434813854b456f92d3ed686e1146285984";
    static final String TEST_FINGERPRINT="f7ae8128469276ec37fee6c7b37a7e3b6ae33117ca034ed183a0f3b3076fcf61";
    private final JdbcTemplate jdbc; private final Environment environment;
    public Stage7C1AccountInitializer(JdbcTemplate jdbc,Environment environment){this.jdbc=jdbc;this.environment=environment;}
    @Override public void run(ApplicationArguments args){
        Boolean allowed=jdbc.queryForObject("""
          SELECT EXISTS (
            SELECT 1 FROM pg_constraint c
            JOIN pg_class r ON r.oid=c.conrelid
            JOIN pg_namespace n ON n.oid=r.relnamespace
            WHERE n.nspname=current_schema() AND r.relname='platform_accounts'
              AND pg_get_constraintdef(c.oid) LIKE '%FAKE_GOOGLE%'
          )
          """,Boolean.class);
        if(!Boolean.TRUE.equals(allowed))return;
        boolean test=Arrays.asList(environment.getActiveProfiles()).contains("test");
        UUID id=test?TEST_UUID:LOCAL_UUID; String ref=test?"stage7c1-google-test":"stage7c1-google-local";
        String env=test?"TEST":"LOCAL";
        String fp=test?TEST_FINGERPRINT:LOCAL_FINGERPRINT;
        jdbc.update("""
          INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,
            external_account_fingerprint,currency,timezone,lifecycle_status,created_at,updated_at,version)
          VALUES (?,'FAKE_GOOGLE',?,?,?,'TWD','Asia/Taipei','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
          ON CONFLICT DO NOTHING
          """,id,env,ref,fp);
        Integer candidates=jdbc.queryForObject("SELECT count(*) FROM platform_accounts WHERE provider_key='FAKE_GOOGLE' AND account_reference=?",Integer.class,ref);
        Integer exact=jdbc.queryForObject("""
          SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='FAKE_GOOGLE'
            AND environment=? AND account_reference=? AND external_account_fingerprint=?
            AND currency='TWD' AND timezone='Asia/Taipei' AND lifecycle_status='ACTIVE' AND archived_at IS NULL
          """,Integer.class,id,env,ref,fp);
        if(candidates==null||candidates!=1||exact==null||exact!=1)throw new IllegalStateException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID");
    }
}
