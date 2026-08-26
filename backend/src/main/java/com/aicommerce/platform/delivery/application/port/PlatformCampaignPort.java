package com.aicommerce.platform.delivery.application.port;
import com.aicommerce.platform.delivery.domain.ProviderKey;
public interface PlatformCampaignPort {
 default ProviderKey providerKey(){return ProviderKey.FAKE;}
 PlatformWriteOutcome submitCampaign(PlatformCampaignCommand command);
 PlatformWriteOutcome changeCampaignState(PlatformStateMutationCommand command);
}
