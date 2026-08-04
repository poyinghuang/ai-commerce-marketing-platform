package com.aicommerce.platform.product.infrastructure.persistence;

import java.util.UUID;

import com.aicommerce.platform.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductJpaRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
}
