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
public final class Stage4BAccountInitializer implements ApplicationRunner {
    public static final UUID LOCAL_UUID=UUID.fromString("00000000-0000-4000-8000-00000000004b");
    public static final UUID TEST_UUID=UUID.fromString("00000000-0000-4000-8000-00000000005b");
    private final JdbcTemplate jdbc; private final Environment environment;
    public Stage4BAccountInitializer(JdbcTemplate jdbc,Environment environment){this.jdbc=jdbc;this.environment=environment;}
    @Override public void run(ApplicationArguments args){
        boolean test=Arrays.asList(environment.getActiveProfiles()).contains("test");
        UUID id=test?TEST_UUID:LOCAL_UUID; String ref=test?"stage4b-test":"stage4b-local";
        String env=test?"TEST":"LOCAL";
        String fp=test?"9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2":"4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36";
        jdbc.update("""
          INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,
            external_account_fingerprint,currency,timezone,lifecycle_status,created_at,updated_at,version)
          VALUES (?,'FAKE',?,?,?,'TWD','Asia/Taipei','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
          ON CONFLICT DO NOTHING
          """,id,env,ref,fp);
        Integer candidates=jdbc.queryForObject("SELECT count(*) FROM platform_accounts WHERE provider_key='FAKE' AND account_reference=?",Integer.class,ref);
        Integer exact=jdbc.queryForObject("""
          SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='FAKE'
            AND environment=? AND account_reference=? AND external_account_fingerprint=?
            AND currency='TWD' AND timezone='Asia/Taipei' AND lifecycle_status='ACTIVE' AND archived_at IS NULL
          """,Integer.class,id,env,ref,fp);
        if(candidates==null||candidates!=1||exact==null||exact!=1)throw new IllegalStateException("PLATFORM_ACCOUNT_CONFIGURATION_INVALID");
    }
}
