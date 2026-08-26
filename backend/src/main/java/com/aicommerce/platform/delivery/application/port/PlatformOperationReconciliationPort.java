package com.aicommerce.platform.delivery.application.port;
import com.aicommerce.platform.delivery.domain.ProviderKey;
public interface PlatformOperationReconciliationPort {
 default ProviderKey providerKey(){return ProviderKey.FAKE;}
 PlatformReconciliationOutcome reconcile(PlatformReconciliationQuery query);
}
