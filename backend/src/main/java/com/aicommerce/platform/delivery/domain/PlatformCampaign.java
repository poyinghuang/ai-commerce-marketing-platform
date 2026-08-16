package com.aicommerce.platform.delivery.domain;
import java.time.Instant; import java.util.UUID; import com.aicommerce.platform.common.persistence.MutableEntity; import jakarta.persistence.*;
@Entity @Table(name="platform_campaigns") public class PlatformCampaign extends MutableEntity {
 @Id @Column(name="platform_campaign_uuid",updatable=false) private UUID platformCampaignUuid; @Column(name="campaign_uuid",updatable=false) private UUID campaignUuid; @Column(name="platform_account_uuid",updatable=false) private UUID platformAccountUuid;
 @Column(name="objective",updatable=false) private String objective; @Enumerated(EnumType.STRING) @Column(name="desired_state") private PlatformDesiredState desiredState; @Column(name="observed_state") private String observedState;
 @Column(name="schedule_start",updatable=false) private Instant scheduleStart; @Column(name="schedule_end",updatable=false) private Instant scheduleEnd; @Column(name="account_timezone",updatable=false) private String accountTimezone; @Column(name="external_id") private String externalId;
 protected PlatformCampaign(){} public UUID getPlatformCampaignUuid(){return platformCampaignUuid;} public UUID getPlatformAccountUuid(){return platformAccountUuid;} public PlatformDesiredState getDesiredState(){return desiredState;} public String getExternalId(){return externalId;}
}
