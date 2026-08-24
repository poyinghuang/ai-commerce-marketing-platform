package com.aicommerce.platform.delivery.application.port;

import java.util.Optional;
import java.util.UUID;

import com.aicommerce.platform.delivery.domain.PlatformDesiredState;
import com.aicommerce.platform.delivery.domain.PlatformEntityType;
import com.aicommerce.platform.delivery.domain.PlatformObservedState;

public interface PlatformDeliveryReadPort {
    DeliveryObservation readObservedState(DeliveryReadCommand command);

    record DeliveryReadCommand(
            UUID platformAccountUuid,
            PlatformEntityType entityType,
            UUID entityUuid,
            String durableExternalId,
            PlatformDesiredState currentDesiredState) {
        public DeliveryReadCommand {
            PlatformContractSupport.req(platformAccountUuid);
            PlatformContractSupport.req(entityType);
            PlatformContractSupport.req(entityUuid);
            PlatformContractSupport.req(durableExternalId);
            if (durableExternalId.isBlank()) throw PlatformContractSupport.invalid();
            PlatformContractSupport.req(currentDesiredState);
        }
    }

    record DeliveryObservation(
            PlatformObservedState observedState,
            Optional<String> safeProviderTraceId) {
        public DeliveryObservation {
            PlatformContractSupport.req(observedState);
            safeProviderTraceId = PlatformContractSupport.opt(safeProviderTraceId);
            safeProviderTraceId.ifPresent(PlatformContractSupport::safe);
        }
    }
}
