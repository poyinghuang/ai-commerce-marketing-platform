package com.aicommerce.platform.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformOperationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(PlatformOperationSameInstantIntegrationTest.FixedClockConfiguration.class)
class PlatformOperationSameInstantIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6-alpine3.22");

    @Autowired PlatformOperationService service;
    @Autowired AuditOperationContextFactory contexts;
    @Autowired JdbcTemplate jdbc;

    @Test
    void sequentialStateMutationsAtTheSameInstantCorrelateToTheirExactOperations() {
        Instant same=Instant.parse("2026-08-17T04:00:00Z");
        UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID();
        jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"same-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name,lifecycle_status,version) values (?,?,'ACTIVE',0)",plan,"Same instant");

        UUID createId=UUID.randomUUID();
        String createPayload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
        var create=service.create(command(createId,account,campaign,PlatformOperationType.CREATE_CAMPAIGN,createPayload),contexts.forCurrentActor("same-create-"+createId));
        service.submit(createId,create.getVersion());

        UUID resumeId=UUID.randomUUID();
        String resumePayload=statePayload(campaign,PlatformOperationType.RESUME,1,"ACTIVE");
        var resume=service.create(command(resumeId,account,campaign,PlatformOperationType.RESUME,resumePayload),contexts.forCurrentActor("same-resume-"+resumeId));
        service.submit(resumeId,resume.getVersion());
        UUID pauseId=UUID.randomUUID();
        String pausePayload=statePayload(campaign,PlatformOperationType.PAUSE,2,"PAUSED");
        var pause=service.create(command(pauseId,account,campaign,PlatformOperationType.PAUSE,pausePayload),contexts.forCurrentActor("same-pause-"+pauseId));
        service.submit(pauseId,pause.getVersion());

        assertThat(jdbc.queryForObject("select desired_state from platform_campaigns where platform_campaign_uuid=?",String.class,campaign)).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("select version from platform_campaigns where platform_campaign_uuid=?",Long.class,campaign)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from platform_operations where operation_uuid in (?,?) and status='SUCCEEDED' and updated_at=?",Integer.class,resumeId,pauseId,Timestamp.from(same))).isEqualTo(2);
    }

    private CreatePlatformOperationCommand command(UUID operation,UUID account,UUID entity,PlatformOperationType type,String payload){
        return new CreatePlatformOperationCommand(operation,account,type,PlatformEntityType.CAMPAIGN,entity,UUID.randomUUID(),payload,3);
    }

    private String statePayload(UUID campaign,PlatformOperationType type,long version,String target){
        return "{\"schemaVersion\":1,\"operationType\":\""+type+"\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"expectedEntityVersion\":"+version+",\"targetDesiredState\":\""+target+"\"}";
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean @Primary Clock stage4aFixedClock(){
            return Clock.fixed(Instant.parse("2026-08-17T04:00:00Z"),ZoneOffset.UTC);
        }
    }
}
