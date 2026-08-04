package com.aicommerce.platform.product.application;

import java.util.UUID;

import com.aicommerce.platform.product.domain.Product;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.product.infrastructure.persistence.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQueryService {

    private final ProductJpaRepository productRepository;

    public ProductQueryService(ProductJpaRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Product findByUuid(UUID productUuid) {
        return productRepository.findById(productUuid).orElseThrow(() -> new ProductNotFoundException(productUuid));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(ProductSearchCriteria criteria, Pageable pageable) {
        return productRepository.findAll(ProductSpecifications.matches(criteria), pageable);
    }
}
