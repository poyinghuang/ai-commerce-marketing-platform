package com.aicommerce.platform.delivery.application.port;
import com.aicommerce.platform.delivery.domain.ProviderKey;
public interface PlatformAdPort {
 default ProviderKey providerKey(){return ProviderKey.FAKE;}
 PlatformWriteOutcome submitAd(PlatformAdCommand command);
 PlatformWriteOutcome changeAdState(PlatformStateMutationCommand command);
}
