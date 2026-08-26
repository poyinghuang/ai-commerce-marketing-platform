package com.aicommerce.platform.delivery.application.port;
import com.aicommerce.platform.delivery.domain.ProviderKey;
public interface PlatformAdSetPort {
 default ProviderKey providerKey(){return ProviderKey.FAKE;}
 PlatformWriteOutcome submitAdSet(PlatformAdSetCommand command);
 PlatformWriteOutcome changeAdSetState(PlatformStateMutationCommand command);
 PlatformWriteOutcome updateAdSetBudget(PlatformBudgetMutationCommand command);
}
