package com.aicommerce.platform.product.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Product p where p.productUuid = :productUuid")
    Optional<Product> findForKnowledgeMutation(@Param("productUuid") UUID productUuid);
}
