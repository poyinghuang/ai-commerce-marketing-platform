package com.aicommerce.platform.connector.drive.application;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.audit.domain.AuditOperationContext;
import com.aicommerce.platform.product.application.*;
import com.aicommerce.platform.product.domain.*;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class ProductStorageFolderService {
    private final ProductStorageFolderQueryService queries; private final ProductJpaRepository products;
    private final StorageProvider provider; private final AuditOperationContextFactory contexts;
    private final ProductStorageFolderPersistenceService persistence;
    public ProductStorageFolderService(ProductStorageFolderQueryService queries,ProductJpaRepository products,StorageProvider provider,
            AuditOperationContextFactory contexts,ProductStorageFolderPersistenceService persistence){this.queries=queries;this.products=products;this.provider=provider;this.contexts=contexts;this.persistence=persistence;}
    public EnsureStorageFolderResult ensure(UUID productUuid,String requestId){
        AuditOperationContext context;
        try{context=contexts.forCurrentActor(requestId);}catch(IllegalStateException e){throw new AuditActorUnavailableException(e);}
        var existing=queries.find(productUuid); if(existing.isPresent())return new EnsureStorageFolderResult(existing.get(),false);
        ProductIdentity product=activeProduct(productUuid);
        StorageFolderTree tree=provider.ensureProductTree(new StorageEnsureRequest(productUuid,product.productId()));
        try{return persistence.persist(productUuid,tree,context);}
        catch(DataIntegrityViolationException race){
            return queries.find(productUuid).map(view->new EnsureStorageFolderResult(view,false)).orElseThrow(()->race);
        }
    }
    @Transactional(readOnly=true)
    protected ProductIdentity activeProduct(UUID productUuid){
        Product product=products.findById(productUuid).orElseThrow(()->new ProductNotFoundException(productUuid));
        if(product.getLifecycleStatus()==ProductLifecycleStatus.ARCHIVED)throw new ProductArchivedException();
        return new ProductIdentity(product.getProductId());
    }
    protected record ProductIdentity(String productId){}
}
