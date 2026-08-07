package com.aicommerce.platform.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aicommerce.platform.audit.application.AuditOperationContextFactory;
import com.aicommerce.platform.product.infrastructure.persistence.ProductJpaRepository;
import com.aicommerce.platform.quality.application.ProductQualityRecalculationService;
import com.aicommerce.platform.quality.application.ProductQualityStartupRepair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductQualityStartupRepairConditionTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(ProductJpaRepository.class, () -> mock(ProductJpaRepository.class))
            .withBean(ProductQualityRecalculationService.class, () -> mock(ProductQualityRecalculationService.class))
            .withBean(AuditOperationContextFactory.class, () -> mock(AuditOperationContextFactory.class))
            .withUserConfiguration(ProductQualityStartupRepair.class);

    @Test
    void repairIsFailClosedWhenTheServerSidePropertyIsAbsentOrFalse() {
        context.run(value -> assertThat(value).doesNotHaveBean(ProductQualityStartupRepair.class));
        context.withPropertyValues("app.quality.startup-repair-enabled=false")
                .run(value -> assertThat(value).doesNotHaveBean(ProductQualityStartupRepair.class));
    }

    @Test
    void repairExistsOnlyAfterExplicitServerSideEnablement() {
        context.withPropertyValues("app.quality.startup-repair-enabled=true")
                .run(value -> assertThat(value).hasSingleBean(ProductQualityStartupRepair.class));
        context.withPropertyValues("spring.profiles.active=production,local",
                        "app.quality.startup-repair-enabled=true")
                .run(value -> assertThat(value).doesNotHaveBean(ProductQualityStartupRepair.class));
    }
}
