package com.aicommerce.platform.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import com.aicommerce.platform.asset.infrastructure.persistence.AssetJpaRepository;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignPlanJpaRepository;
import com.aicommerce.platform.campaign.infrastructure.persistence.CampaignProductJpaRepository;
import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.knowledge.infrastructure.persistence.ProductKnowledgeJpaRepository;
import org.junit.jupiter.api.Test;

class ArchivableResourceRepositoryContractTest {

    private static final List<Class<?>> ARCHIVABLE_REPOSITORIES = List.of(
            ProductKnowledgeJpaRepository.class,
            CreativePlanJpaRepository.class,
            CampaignPlanJpaRepository.class,
            CampaignProductJpaRepository.class,
            AssetJpaRepository.class);

    private static final Set<String> HARD_DELETE_METHODS = Set.of(
            "delete",
            "deleteAll",
            "deleteAllById",
            "deleteAllByIdInBatch",
            "deleteAllInBatch",
            "deleteById",
            "deleteInBatch");

    @Test
    void archivableRepositoryContractsExposeNoHardDeleteMethod() {
        for (Class<?> repository : ARCHIVABLE_REPOSITORIES) {
            assertThat(repository.getMethods())
                    .extracting(Method::getName)
                    .doesNotContainAnyElementsOf(HARD_DELETE_METHODS);
            assertThat(repository.getInterfaces()).containsExactly(ArchivableResourceRepository.class);
        }
    }
}
