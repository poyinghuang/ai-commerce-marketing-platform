package com.aicommerce.platform.quality.application;

import java.util.UUID;

import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreBlockerJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.QualityScoreJpaRepository;
import com.aicommerce.platform.quality.infrastructure.persistence.WorkflowStatusJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductQualityQueryService {
    private final ProductJpaRepository products;
    private final QualityScoreJpaRepository scores;
    private final QualityScoreBlockerJpaRepository blockers;
    private final WorkflowStatusJpaRepository workflows;
    public ProductQualityQueryService(ProductJpaRepository products, QualityScoreJpaRepository scores,
            QualityScoreBlockerJpaRepository blockers, WorkflowStatusJpaRepository workflows) {
        this.products = products; this.scores = scores; this.blockers = blockers; this.workflows = workflows;
    }
    @Transactional(readOnly = true)
    public QualityProjectionView get(UUID productUuid) {
        if (!products.existsById(productUuid)) throw new ProductNotFoundException(productUuid);
        var score = scores.findByProductUuid(productUuid).orElseThrow(QualityNotFoundException::new);
        var workflow = workflows.findByProductUuid(productUuid).orElseThrow(QualityNotFoundException::new);
        return QualityProjectionView.from(score,
                blockers.findByQualityScoreUuidOrderByBlockerCode(score.getQualityScoreUuid()), workflow);
    }
}
