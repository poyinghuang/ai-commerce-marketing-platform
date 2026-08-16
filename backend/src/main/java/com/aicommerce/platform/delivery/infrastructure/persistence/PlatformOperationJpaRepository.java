package com.aicommerce.platform.delivery.infrastructure.persistence;
import java.util.Optional; import java.util.UUID; import com.aicommerce.platform.delivery.domain.PlatformOperation; import org.springframework.data.jpa.repository.JpaRepository;
public interface PlatformOperationJpaRepository extends JpaRepository<PlatformOperation,UUID> {
 Optional<PlatformOperation> findByPlatformAccountUuidAndRequestedActorTypeAndRequestedActorIdAndClientRequestUuid(UUID account,String actorType,String actorId,UUID request);
 Optional<PlatformOperation> findByPlatformAccountUuidAndIdempotencyKey(UUID account,String key);
}
