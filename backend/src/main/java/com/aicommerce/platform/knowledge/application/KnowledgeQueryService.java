package com.aicommerce.platform.knowledge.application;
import java.util.UUID;
import com.aicommerce.platform.common.domain.LifecycleStatus;
import com.aicommerce.platform.knowledge.domain.ProductKnowledge;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class KnowledgeQueryService {
    private final ProductKnowledgeJpaRepository repository; private final ProductJpaRepository products;
    public KnowledgeQueryService(ProductKnowledgeJpaRepository repository, ProductJpaRepository products) { this.repository = repository; this.products = products; }
    @Transactional(readOnly=true) public ProductKnowledge get(UUID productUuid, UUID knowledgeUuid) { requireProduct(productUuid); return repository.findOwned(productUuid, knowledgeUuid).orElseThrow(KnowledgeNotFoundException::new); }
    @Transactional(readOnly=true) public Page<ProductKnowledge> list(UUID productUuid, LifecycleStatus status, Pageable pageable) { requireProduct(productUuid); return repository.findByProductUuidAndStatus(productUuid, status, pageable); }
    private void requireProduct(UUID uuid) { if (!products.existsById(uuid)) throw new ProductNotFoundException(uuid); }
}
