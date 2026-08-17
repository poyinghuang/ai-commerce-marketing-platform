package com.aicommerce.platform.delivery.application.port;
public interface PlatformCampaignPort { PlatformWriteOutcome submitCampaign(PlatformCampaignCommand command); PlatformWriteOutcome changeCampaignState(PlatformStateMutationCommand command); }
